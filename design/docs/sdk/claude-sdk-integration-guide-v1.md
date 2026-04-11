# Claude IDE 集成插件开发指南

> 本文档基于 JetBrains CC-GUI 插件项目源码分析，总结 Claude 供应商管理与消息渲染输出的完整机制，为开发类似 IDE 集成插件提供可复用方案。

---

## 目录

1. [整体架构概览](#1-整体架构概览)
2. [供应商多配置机制](#2-供应商多配置机制)
3. [消息渲染输出机制](#3-消息渲染输出机制)
4. [关键代码全局索引](#4-关键代码全局索引)
5. [可复用插件开发方案](#5-可复用插件开发方案)

---

## 1. 整体架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                         IDE 插件层                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │   Provider      │  │    Session      │  │     Bridge      │  │
│  │   Manager       │  │    Manager      │  │    Handler      │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
└───────────┼────────────────────┼────────────────────┼───────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Java/Backend 层                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ ClaudeSDKBridge  │  │ StreamMessage   │  │   Daemon        │  │
│  │                 │  │ Coalescer       │  │   Bridge        │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │            │
│           └────────────────────┼────────────────────┘            │
│                                ▼                                 │
│                    ┌─────────────────────┐                        │
│                    │  ClaudeStream       │                        │
│                    │  Adapter (标签协议) │                        │
│                    └──────────┬──────────┘                        │
└───────────────────────────────┼─────────────────────────────────┘
                                │ NDJSON / Tagged Output
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Node.js SDK 层                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  Claude SDK      │  │  Message        │  │    Tool         │  │
│  │  (cli)          │  │  Parser         │  │    Executor     │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ HTTP/WebSocket
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Claude API                                  │
│  (Anthropic / OpenRouter / 自建 Proxy)                           │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| IDE 插件框架 | IntelliJ Platform SDK | 支持 JetBrains 全家桶 |
| 后端语言 | Java 17+ | 插件主体逻辑 |
| 前端渲染 | React 18 + TypeScript | WebView UI |
| 状态管理 | React Hooks (useRef/useState) | 无外部状态库 |
| 样式 | Less CSS | 主题变量支持 |
| 通信 | JCEF (Java Chromium) | WebView 与 Java 桥接 |

---

## 2. 供应商多配置机制

### 2.1 核心概念

**供应商 (Provider)** 是 Claude API 的配置封装，包含：
- `id`: 唯一标识
- `name`: 显示名称
- `settingsConfig`: 环境变量配置
  - `ANTHROPIC_AUTH_TOKEN`: API 密钥
  - `ANTHROPIC_BASE_URL`: API 端点
  - `ANTHROPIC_DEFAULT_SONNET_MODEL`: 模型映射
  - `alwaysThinkingEnabled`: 是否启用思考过程
- `permissions`: 权限配置

### 2.2 特殊供应商 ID

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPECIAL_PROVIDER_IDS                          │
├──────────────┬──────────────────────────────────────────────────┤
│ __disabled__ │ 禁用状态 - 无活动供应商                           │
├──────────────┼──────────────────────────────────────────────────┤
│ __local_     │ 本地模式 - 使用 ~/.claude/settings.json          │
│ settings_    │                                                  │
│ json__       │                                                  │
├──────────────┼──────────────────────────────────────────────────┤
│ __cli_login__ │ CLI登录模式 - 通过 claude login 认证             │
└──────────────┴──────────────────────────────────────────────────┘
```

### 2.3 供应商预设 (Provider Presets)

```typescript
// webview/src/types/provider.ts

export const PROVIDER_PRESETS = [
  { id: 'zhipu',   nameKey: 'settings.provider.presets.zhipu',   env: {...} },
  { id: 'kimi',    nameKey: 'settings.provider.presets.kimi',    env: {...} },
  { id: 'deepseek',nameKey: 'settings.provider.presets.deepseek', env: {...} },
  { id: 'minimax', nameKey: 'settings.provider.presets.minimax',  env: {...} },
  { id: 'xiaomi',  nameKey: 'settings.provider.presets.xiaomi',   env: {...} },
  { id: 'qwen',    nameKey: 'settings.provider.presets.qwen',     env: {...} },
  { id: 'openrouter', nameKey: 'settings.provider.presets.openrouter', env: {...} },
];
```

每个预设包含：
```typescript
{
  id: 'minimax',
  nameKey: 'settings.provider.presets.minimax',  // i18n key
  env: {
    ANTHROPIC_BASE_URL: 'https://api.minimaxi.com/anthropic',
    ANTHROPIC_AUTH_TOKEN: '',
    API_TIMEOUT_MS: '3000000',  // 3分钟超时
    ANTHROPIC_DEFAULT_SONNET_MODEL: 'MiniMax-M2.1',
  }
}
```

### 2.4 供应商管理核心类

```java
// src/main/java/.../settings/ProviderManager.java

public class ProviderManager {
    // 三大特殊供应商ID
    public static final String DISABLED_PROVIDER_ID = "__disabled__";
    public static final String LOCAL_SETTINGS_PROVIDER_ID = "__local_settings_json__";
    public static final String CLI_LOGIN_PROVIDER_ID = "__cli_login__";

    // 核心方法
    public List<JsonObject> getClaudeProviders();        // 获取所有供应商
    public JsonObject getActiveClaudeProvider();         // 获取当前供应商
    public void switchClaudeProvider(String id);         // 切换供应商
    public void addClaudeProvider(JsonObject provider);  // 添加供应商
    public void updateClaudeProvider(String id, JsonObject updates);  // 更新
    public DeleteResult deleteClaudeProvider(String id);  // 删除
    public void saveProviderOrder(List<String> orderedIds); // 保存顺序
}
```

### 2.5 供应商切换流程

```
用户选择供应商
       │
       ▼
ClaudeProviderOperations.handleSwitchProvider(id)
       │
       ├─ if (id == "__disabled__")
       │       └── deactivateClaudeProvider()
       │
       ├─ if (id == "__local_settings_json__")
       │       └── 验证 ~/.claude/settings.json
       │       └── applyLocalSettingsToClaudeSettings()
       │
       ├─ if (id == "__cli_login__")
       │       └── applyCliLoginToClaudeSettings()
       │
       └─ else (自定义供应商)
               └── switchClaudeProvider(id)
               └── applyActiveProviderToClaudeSettings()
```

### 2.6 思考模式配置

```java
// 在活动供应商中设置 alwaysThinkingEnabled
public boolean setAlwaysThinkingEnabledInActiveProvider(boolean enabled) {
    JsonObject provider = providers.getAsJsonObject(currentId);
    JsonObject settingsConfig = provider.getAsJsonObject("settingsConfig");
    settingsConfig.addProperty("alwaysThinkingEnabled", enabled);
}
```

---

## 3. 消息渲染输出机制

### 3.1 内容块类型定义

```typescript
// webview/src/types/index.ts

export type ClaudeContentBlock =
  | { type: 'text'; text?: string }                                    // 文本
  | { type: 'thinking'; thinking?: string; text?: string }           // 思考过程
  | { type: 'tool_use'; id?: string; name?: string; input?: ToolInput }  // 工具调用
  | { type: 'image'; src?: string; mediaType?: string; alt?: string } // 图片
  | { type: 'attachment'; fileName?: string; mediaType?: string };    // 附件

export interface ToolResultBlock {
  type: 'tool_result';
  tool_use_id?: string;
  content?: string | Array<{ type?: string; text?: string }>;
  is_error?: boolean;
}

export interface ClaudeMessage {
  type: 'user' | 'assistant' | 'error';
  content?: string;
  raw?: ClaudeRawMessage | string;
  timestamp?: string;
  isStreaming?: boolean;
  __turnId?: number;  // 流式turn标识
}
```

### 3.2 后端标签协议

```java
// src/main/java/.../provider/claude/ClaudeStreamAdapter.java

// ===== 消息标签 =====
[MESSAGE]           // 完整消息 (JSON)
[MESSAGE_START]     // 消息开始
[MESSAGE_END]       // 消息结束

// ===== 内容流标签 =====
[CONTENT]           // 完整内容块
[CONTENT_DELTA]     // 内容增量 (streaming delta)

// ===== Thinking流标签 =====
[THINKING]          // 完整思考过程
[THINKING_DELTA]    // 思考增量

// ===== 控制标签 =====
[STREAM_START]      // 流开始
[STREAM_END]        // 流结束
[SESSION_ID]        // 会话ID
[USAGE]             // 使用统计
[TOOL_RESULT]       // 工具结果

// ===== 错误标签 =====
[SEND_ERROR]        // 发送错误
[STDIN_ERROR]       // stdin错误
```

### 3.3 流式消息处理流程

```
后端 (Java)
    │
    ▼
ClaudeStreamAdapter.processOutputLine(line)
    │
    ├── [THINKING_DELTA] ──────────────► callback.onMessage("thinking_delta", delta)
    │
    ├── [CONTENT_DELTA] ──────────────► callback.onMessage("content_delta", delta)
    │
    ├── [STREAM_START] ───────────────► callback.onMessage("stream_start", "")
    │
    ├── [STREAM_END] ─────────────────► callback.onMessage("stream_end", "")
    │
    ├── [TOOL_RESULT] ────────────────► callback.onMessage("tool_result", result)
    │
    └── [USAGE] ──────────────────────► callback.onMessage("usage", usage)
                                        │
                                        ▼
                               StreamMessageCoalescer
                                        │
                                        ├── 自适应节流 (50ms-5s)
                                        │
                                        └── updateMessages(json) ──► 前端
```

### 3.4 自适应节流机制

```java
// src/main/java/.../session/StreamMessageCoalescer.java

// 根据 payload 大小动态调整推送间隔
if (payload < 100KB)      → 50ms   // 正常间隔
if (payload 100-200KB)     → 500ms  // 中等间隔
if (payload 200-500KB)     → 2000ms // 大间隔
if (payload > 500KB)       → 5000ms // 超大间隔

// 10秒心跳防止工具执行阶段 stream stall
private static final int HEARTBEAT_INTERVAL_MS = 10_000;
window.onStreamingHeartbeat();  // 前端心跳回调
```

### 3.5 前端流式回调

```typescript
// webview/src/hooks/.../streamingCallbacks.ts

window.onStreamStart = () => {
  streamingContentRef.current = '';
  isStreamingRef.current = true;
  startStallWatchdog();  // 启动流卡住监控
  // 创建空的 assistant 消息
};

window.onContentDelta = (delta: string) => {
  // 累加文本内容
  streamingContentRef.current += delta;
  // 50ms 节流更新 UI
  if (timeSinceLastUpdate >= THROTTLE_INTERVAL) {
    updateMessages();
  } else {
    // 延迟更新
    setTimeout(updateMessages, remainingTime);
  }
};

window.onThinkingDelta = (delta: string) => {
  // 累加思考内容
  // 自动展开最新的 thinking 块
  // 50ms 节流更新
};

window.onStreamEnd = () => {
  // 取消 pending 的节流 timeout
  // flush 最终内容
  // 清理所有流式 refs
  // 折叠自动展开的 thinking 块
};
```

### 3.6 消息解析与规范化

```typescript
// webview/src/utils/messageUtils.ts

function normalizeBlocks(raw: ClaudeRawMessage | string): ClaudeContentBlock[] | null {
  // 支持多种格式
  // 1. 字符串 → [{ type: 'text', text }]
  // 2. raw.message?.content (数组) → 遍历解析
  // 3. raw.content (数组)

  // 解析每个 block
  if (type === 'text')      → { type: 'text', text }
  if (type === 'thinking')  → { type: 'thinking', thinking, text }
  if (type === 'tool_use')  → { type: 'tool_use', id, name, input }
  if (type === 'image')     → { type: 'image', src, mediaType }
  if (type === 'attachment') → { type: 'attachment', fileName, mediaType }
}
```

### 3.7 思考过程 (Thinking) UI

```tsx
// webview/src/components/MessageItem/ContentBlockRenderer.tsx

if (block.type === 'thinking') {
  return (
    <div className="thinking-block">
      <div className="thinking-header" onClick={onToggleThinking}>
        <span className="thinking-title">
          {isStreaming && isLastMessage
            ? t('common.thinkingProcess')  // 流式时显示
            : t('common.thinking')}
        </span>
        <span className="thinking-icon">
          {isExpanded ? '▼' : '▶'}
        </span>
      </div>
      <div className="thinking-content" style={{ display: isExpanded ? 'block' : 'none' }}>
        <MarkdownBlock content={block.thinking} isStreaming={isStreaming} />
      </div>
    </div>
  );
}
```

**Thinking 规范化处理**:
```typescript
const normalizeThinking = (thinking: string): string => {
  return thinking
    .replace(/\r\n?/g, '\n')           // 统一换行符
    .replace(/\n[ \t]*\n+/g, '\n')    // 合并多个空行
    .replace(/^\n+/, '')              // 移除开头换行
    .replace(/\n+$/, '');             // 移除结尾换行
};
```

### 3.8 工具调用 UI 分发

```typescript
// ContentBlockRenderer.tsx

if (block.type === 'tool_use') {
  const toolName = normalizeToolName(block.name);

  if (toolName === 'edit') {
    return <EditToolBlock name={block.name} input={block.input} result={...} />;
  }
  if (toolName === 'bash' || toolName === 'shell') {
    return <BashToolBlock name={block.name} input={block.input} result={...} />;
  }
  if (toolName === 'read' || toolName === 'glob' || toolName === 'grep') {
    return <ReadToolBlock name={block.name} input={block.input} result={...} />;
  }
  if (toolName === 'task' || toolName === 'agent') {
    return <TaskExecutionBlock name={block.name} input={block.input} result={...} />;
  }
  return <GenericToolBlock name={block.name} input={block.input} result={...} />;
}
```

### 3.9 Diff 记录展示

```typescript
// webview/src/components/toolBlocks/EditToolBlock.tsx

// LCS 算法计算 Diff
function computeDiff(oldLines: string[], newLines: string[]): DiffResult {
  const dp: number[][] = Array(m + 1).fill(null).map(() => Array(n + 1).fill(0));

  // 构建 DP 表
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (oldLines[i-1] === newLines[j-1]) {
        dp[i][j] = dp[i-1][j-1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
      }
    }
  }

  // 回溯生成 diff
  // 返回 { lines: [{type, content}], additions, deletions }
}

// Diff UI 渲染
{diff.lines.map((line, index) => (
  <div style={{
    background: line.type === 'deleted' ? 'var(--diff-deleted-bg)' :
                line.type === 'added'   ? 'var(--diff-added-bg)' :
                'transparent',
  }}>
    <div style={{ color: line.type === 'deleted' ? 'var(--diff-deleted-accent)' :
                        line.type === 'added'   ? 'var(--diff-added-accent)' :
                        'var(--diff-muted-text)' }}>
      {line.type === 'deleted' ? '-' :
       line.type === 'added'   ? '+' : ' '}
    </div>
    <pre>{line.content}</pre>
  </div>
))}
```

### 3.10 Markdown 渲染

```typescript
// webview/src/components/MarkdownBlock.tsx

// 流式期间使用轻量级渲染
if (isStreaming) {
  return renderStreamingContent(content);  // 不调用 marked.parse()
}

// 非流式使用完整渲染
const parsed = marked.parse(content);
const sanitized = DOMPurify.sanitize(parsed);

// 特性
// - highlight.js 语法高亮
// - Mermaid 图表延迟加载
// - 代码块复制按钮
// - 图片预览
```

### 3.11 UI 渲染组件树

```
MessageList.tsx
    │
    └── MessageItem.tsx
            │
            ├── groupBlocks()  // 内容块分组
            │   ├── single           // 单个 block
            │   ├── read_group      // 多个 read 工具
            │   ├── edit_group      // 多个 edit 工具
            │   ├── bash_group      // 多个 bash 工具
            │   └── search_group    // 多个搜索工具
            │
            └── renderGroupedBlocks()
                    │
                    └── ContentBlockRenderer
                            │
                            ├── text         → MarkdownBlock.tsx
                            ├── thinking     → 折叠思考块
                            ├── tool_use
                            │       ├── edit  → EditToolBlock.tsx (含Diff)
                            │       ├── bash  → BashToolBlock.tsx
                            │       ├── read  → ReadToolBlock.tsx
                            │       ├── task  → TaskExecutionBlock.tsx
                            │       └── *     → GenericToolBlock.tsx
                            ├── image        → 图片预览
                            └── attachment    → 附件芯片
```

---

## 4. 关键代码全局索引

> **索引说明**: 本索引基于项目根目录 `E:\work-File\code\jetbrains-cc-gui` 提供绝对路径，其他项目可直接引用对应文件。

### 4.1 供应商管理

| 功能 | 系统绝对路径 |
|------|-------------|
| **供应商类型定义** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\provider.ts` |
| **供应商管理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\ProviderManager.java` |
| **供应商操作处理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\handler\provider\ClaudeProviderOperations.java` |
| **供应商排序辅助** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\ProviderOrderHelper.java` |
| **Claude设置管理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\ClaudeSettingsManager.java` |
| **Provider导入导出** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\handler\provider\ProviderImportExportSupport.java` |
| **Provider排序服务** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\handler\provider\ProviderOrderingService.java` |

### 4.2 流式消息处理

| 功能 | 系统绝对路径 |
|------|-------------|
| **流式适配器 (标签解析)** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeStreamAdapter.java` |
| **消息聚合器 (节流)** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\StreamMessageCoalescer.java` |
| **SDK桥接器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeSDKBridge.java` |
| **Daemon管理** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\common\DaemonBridge.java` |
| **请求参数构建器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeRequestParamsBuilder.java` |
| **消息回调接口** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\common\MessageCallback.java` |
| **Daemon协调器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeDaemonCoordinator.java` |
| **Daemon请求执行器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeDaemonRequestExecutor.java` |
| **进程调用器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeProcessInvoker.java` |
| **基础SDK桥接** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\common\BaseSDKBridge.java` |
| **JSON输出提取器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeJsonOutputExtractor.java` |
| **消息处理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\ClaudeMessageHandler.java` |
| **消息解析器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\MessageParser.java` |
| **Delta节流器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\StreamDeltaThrottler.java` |

### 4.3 前端消息处理

| 功能 | 系统绝对路径 |
|------|-------------|
| **流式消息Hook** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useStreamingMessages.ts` |
| **流式回调注册** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\windowCallbacks\registerCallbacks\streamingCallbacks.ts` |
| **消息回调注册** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\windowCallbacks\registerCallbacks\messageCallbacks.ts` |
| **消息同步工具** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\windowCallbacks\messageSync.ts` |
| **消息解析工具** | `E:\work-File\code\jetbrains-cc-gui\webview\src\utils\messageUtils.ts` |
| **窗口回调Hook** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useWindowCallbacks.ts` |
| **消息处理Hook** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useMessageProcessing.ts` |
| **消息队列** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useMessageQueue.ts` |
| **消息发送Hook** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useMessageSender.ts` |
| **会话管理** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useSessionManagement.ts` |
| **历史加载** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\useHistoryLoader.ts` |

### 4.4 UI 渲染组件

| 功能 | 系统绝对路径 |
|------|-------------|
| **消息列表** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\MessageList.tsx` |
| **单条消息** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\MessageItem\MessageItem.tsx` |
| **内容块渲染器** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\MessageItem\ContentBlockRenderer.tsx` |
| **Markdown渲染** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\MarkdownBlock.tsx` |
| **编辑工具 (含Diff)** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\EditToolBlock.tsx` |
| **Bash工具** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\BashToolBlock.tsx` |
| **读取工具** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\ReadToolBlock.tsx` |
| **通用工具** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\GenericToolBlock.tsx` |
| **任务执行** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\TaskExecutionBlock.tsx` |

### 4.5 工具块分组

| 功能 | 系统绝对路径 |
|------|-------------|
| **编辑工具组** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\EditToolGroupBlock.tsx` |
| **Bash工具组** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\BashToolGroupBlock.tsx` |
| **读取工具组** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\ReadToolGroupBlock.tsx` |
| **搜索工具组** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\SearchToolGroupBlock.tsx` |
| **工具块导出** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\toolBlocks\index.ts` |

### 4.6 消息类型定义

| 功能 | 系统绝对路径 |
|------|-------------|
| **核心类型定义** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\index.ts` |
| **Provider类型** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\provider.ts` |
| **工具类型** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\tool.ts` |
| **Agent类型** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\agent.ts` |
| **MCP类型** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\mcp.ts` |
| **Usage类型** | `E:\work-File\code\jetbrains-cc-gui\webview\src\types\usage.ts` |

### 4.7 会话管理

| 功能 | 系统绝对路径 |
|------|-------------|
| **会话主类** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\ClaudeSession.java` |
| **会话生命周期管理** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\SessionLifecycleManager.java` |
| **会话上下文服务** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\SessionContextService.java` |
| **会话发送服务** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\SessionSendService.java` |
| **会话消息编排器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\SessionMessageOrchestrator.java` |
| **会话加载服务** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\session\SessionLoadService.java` |
| **历史搜索服务** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeHistorySearchService.java` |
| **历史解析器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\provider\claude\ClaudeHistoryParser.java` |

### 4.8 桥接通信

| 功能 | 系统绝对路径 |
|------|-------------|
| **JavaScript桥接** | `E:\work-File\code\jetbrains-cc-gui\webview\src\utils\bridge.ts` |
| **窗口回调注册** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\windowCallbacks\registerCallbacks.ts` |
| **会话转换** | `E:\work-File\code\jetbrains-cc-gui\webview\src\hooks\windowCallbacks\sessionTransition.ts` |
| **Bridge目录解析器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\bridge\BridgeDirectoryResolver.java` |
| **环境配置器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\bridge\EnvironmentConfigurator.java` |
| **进程管理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\bridge\ProcessManager.java` |
| **Node检测器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\bridge\NodeDetector.java` |

### 4.9 设置管理

| 功能 | 系统绝对路径 |
|------|-------------|
| **配置路径管理** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\ConfigPathManager.java` |
| **Agent管理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\AgentManager.java` |
| **技能管理器** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\SkillManager.java` |
| **MCP服务器管理** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\McpServerManager.java` |
| **工作目录管理** | `E:\work-File\code\jetbrains-cc-gui\src\main\java\com\github\claudecodegui\settings\WorkingDirectoryManager.java` |

### 4.10 Provider设置UI

| 功能 | 系统绝对路径 |
|------|-------------|
| **Provider列表** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\settings\ProviderList\index.tsx` |
| **Provider管理区块** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\settings\ProviderManageSection\index.tsx` |
| **Provider Hook** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\settings\hooks\useProviderManagement.ts` |
| **设置主页面** | `E:\work-File\code\jetbrains-cc-gui\webview\src\components\settings\index.tsx` |

### 4.11 样式文件

| 功能 | 系统绝对路径 |
|------|-------------|
| **消息样式** | `E:\work-File\code\jetbrains-cc-gui\webview\src\styles\less\components\message.less` |
| **工具块样式** | `E:\work-File\code\jetbrains-cc-gui\webview\src\styles\less\components\tool-blocks.less` |
| **主题变量** | `E:\work-File\code\jetbrains-cc-gui\webview\src\styles\less\themes\*.less` |

---

## 附录 A: 核心文件速查 (相对路径版)

> 适用于快速从项目根目录定位

```
# 供应商管理
src/main/java/com/github/claudecodegui/settings/ProviderManager.java
src/main/java/com/github/claudecodegui/handler/provider/ClaudeProviderOperations.java

# 流式消息
src/main/java/com/github/claudecodegui/provider/claude/ClaudeStreamAdapter.java
src/main/java/com/github/claudecodegui/session/StreamMessageCoalescer.java
src/main/java/com/github/claudecodegui/provider/claude/ClaudeSDKBridge.java

# 前端消息
webview/src/hooks/useStreamingMessages.ts
webview/src/hooks/windowCallbacks/registerCallbacks/streamingCallbacks.ts
webview/src/hooks/windowCallbacks/registerCallbacks/messageCallbacks.ts
webview/src/hooks/windowCallbacks/messageSync.ts
webview/src/utils/messageUtils.ts

# UI组件
webview/src/components/MessageItem/MessageItem.tsx
webview/src/components/MessageItem/ContentBlockRenderer.tsx
webview/src/components/toolBlocks/EditToolBlock.tsx
webview/src/components/MarkdownBlock.tsx

# 类型
webview/src/types/index.ts
webview/src/types/provider.ts
```

---

## 5. 可复用插件开发方案

### 5.1 项目结构模板

```
my-claude-plugin/
├── src/main/java/com/myplugin/
│   ├── provider/                    # 供应商管理
│   │   ├── MyProviderManager.java
│   │   ├── MySDKBridge.java
│   │   └── MyStreamAdapter.java
│   ├── session/                     # 会话管理
│   │   ├── MySessionManager.java
│   │   └── MyMessageCoalescer.java
│   ├── handler/                     # 消息处理
│   │   └── MyMessageHandler.java
│   ├── bridge/                      # JCEF桥接
│   │   └── MyJBCefHandler.java
│   └── settings/                    # 设置管理
│       └── MySettingsManager.java
│
├── webview/src/
│   ├── types/                      # 类型定义
│   │   ├── index.ts                 # 核心类型 (ClaudeMessage, ContentBlock)
│   │   └── provider.ts              # 供应商类型
│   │
│   ├── hooks/                       # React Hooks
│   │   ├── useStreamingMessages.ts  # 流式状态管理
│   │   ├── useWindowCallbacks.ts    # 窗口回调注册
│   │   └── windowCallbacks/
│   │       ├── registerCallbacks/
│   │       │   ├── streamingCallbacks.ts
│   │       │   └── messageCallbacks.ts
│   │       └── messageSync.ts
│   │
│   ├── components/                  # UI组件
│   │   ├── MessageList.tsx
│   │   ├── MessageItem/
│   │   │   ├── MessageItem.tsx
│   │   │   └── ContentBlockRenderer.tsx
│   │   ├── toolBlocks/
│   │   │   ├── EditToolBlock.tsx    # 含Diff展示
│   │   │   ├── BashToolBlock.tsx
│   │   │   ├── ReadToolBlock.tsx
│   │   │   └── GenericToolBlock.tsx
│   │   └── MarkdownBlock.tsx
│   │
│   ├── utils/                      # 工具函数
│   │   ├── messageUtils.ts          # 消息解析
│   │   ├── bridge.ts                # Java通信
│   │   └── helpers.ts
│   │
│   └── styles/less/                # 样式
│       └── components/message.less
│
└── resources/
    └── META-INF/
        └── plugin.xml
```

### 5.2 核心接口设计

#### 5.2.1 后端流式适配器接口

```java
// StreamAdapter.java
public interface StreamAdapter {
    /**
     * 处理一行输出，解析标签协议
     */
    void processOutputLine(
        String line,
        MessageCallback callback,
        SDKResult result,
        StringBuilder assistantContent,
        boolean[] hadSendError,
        String[] lastNodeError
    );
}

/**
 * 消息回调接口
 */
public interface MessageCallback {
    void onMessage(String type, String content);
    void onError(String error);
}

// 标签协议常量
public static class Tags {
    public static final String MESSAGE = "[MESSAGE]";
    public static final String CONTENT = "[CONTENT]";
    public static final String CONTENT_DELTA = "[CONTENT_DELTA]";
    public static final String THINKING = "[THINKING]";
    public static final String THINKING_DELTA = "[THINKING_DELTA]";
    public static final String STREAM_START = "[STREAM_START]";
    public static final String STREAM_END = "[STREAM_END]";
    public static final String TOOL_RESULT = "[TOOL_RESULT]";
    public static final String USAGE = "[USAGE]";
    public static final String SESSION_ID = "[SESSION_ID]";
    public static final String SEND_ERROR = "[SEND_ERROR]";
}
```

#### 5.2.2 前端窗口回调类型

```typescript
// windowCallbacks.d.ts

interface StreamingCallbacks {
  onStreamStart: () => void;
  onContentDelta: (delta: string) => void;
  onThinkingDelta: (delta: string) => void;
  onStreamEnd: (sequence?: string | number) => void;
  onStreamingHeartbeat: () => void;
  onPermissionDenied: () => void;
}

interface MessageCallbacks {
  updateMessages: (json: string, sequence?: number) => void;
  showLoading: (value: boolean) => void;
  showThinkingStatus: (value: boolean) => void;
  updateStatus: (text: string) => void;
  clearMessages: () => void;
  addErrorMessage: (message: string) => void;
  addHistoryMessage: (message: ClaudeMessage) => void;
}
```

#### 5.2.3 内容块渲染接口

```typescript
// ContentBlockRendererProps.ts

export interface ContentBlockRendererProps {
  block: ClaudeContentBlock;
  messageIndex: number;
  messageType: 'user' | 'assistant' | 'error';
  isStreaming: boolean;
  isThinkingExpanded: boolean;
  isThinking: boolean;
  isLastMessage: boolean;
  isLastBlock?: boolean;
  t: TFunction;
  onToggleThinking: () => void;
  findToolResult: (toolId: string | undefined, messageIndex: number) => ToolResultBlock | null | undefined;
}
```

### 5.3 供应商管理实现要点

#### 5.3.1 特殊供应商 ID 设计

```typescript
// 设计原则：使用下划线包裹的特殊ID，避免与用户自定义ID冲突

export const SPECIAL_PROVIDER_IDS = {
  DISABLED: '__disabled__',
  LOCAL_SETTINGS: '__local_settings_json__',
  CLI_LOGIN: '__cli_login__',
} as const;

export function isSpecialProviderId(id: string): boolean {
  return Object.values(SPECIAL_PROVIDER_IDS).includes(id as any);
}
```

#### 5.3.2 供应商预设设计

```typescript
// 可扩展的预设配置

export interface ProviderPreset {
  id: string;                    // 唯一标识
  nameKey: string;               // i18n key
  env: Record<string, string>;    // 环境变量
  apiTimeoutMs?: string;         // 可选：API超时
}

export const PROVIDER_PRESETS: ProviderPreset[] = [
  // 官方
  { id: 'anthropic', nameKey: 'provider.presets.anthropic', env: {...} },
  // 第三方
  { id: 'openrouter', nameKey: 'provider.presets.openrouter', env: {...} },
  // 中国区
  { id: 'zhipu', nameKey: 'provider.presets.zhipu', env: {...} },
];
```

#### 5.3.3 供应商切换状态机

```
                    ┌─────────────────┐
                    │   无供应商      │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌──────────────┐  ┌────────────┐
     │ __disabled__│  │ __local_     │  │ __cli_login__│
     │            │  │ settings__   │  │            │
     └────────────┘  └──────────────┘  └────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  自定义供应商    │
                    │ (providers[id]) │
                    └─────────────────┘
```

### 5.4 流式消息处理实现要点

#### 5.4.1 标签协议设计

```java
// 原则：使用方括号包裹的标签，便于解析和扩展

// 消息格式
[MESSAGE]{json}

// 增量格式
[CONTENT_DELTA]{"payload":"..."}

// 完整块格式
[CONTENT]{text content}
[THINKING]{thinking content}

// 控制信号
[STREAM_START]
[STREAM_END]
[SESSION_ID]{session-id}
[USAGE]{tokens: 123, cost: 0.01}
```

#### 5.4.2 前端流式状态管理

```typescript
// useStreamingMessages.ts - 核心Hook模式

interface StreamingState {
  // 内容累积
  streamingContentRef: React.MutableRefObject<string>;
  streamingTextSegmentsRef: React.MutableRefObject<string[]>;
  streamingThinkingSegmentsRef: React.MutableRefObject<string[]>;

  // 活跃段索引
  activeTextSegmentIndexRef: React.MutableRefObject<number>;
  activeThinkingSegmentIndexRef: React.MutableRefObject<number>;

  // 节流控制
  contentUpdateTimeoutRef: React.MutableRefObject<ReturnType<typeof setTimeout> | null>;
  lastContentUpdateRef: React.MutableRefObject<number>;
  THROTTLE_INTERVAL: 50;  // ms
}

// 核心逻辑
export function useStreamingMessages(): StreamingState {
  // 1. onContentDelta → 累加到 streamingContentRef + textSegment
  // 2. onThinkingDelta → 累加到 thinkingSegment
  // 3. 节流更新 UI (50ms)
  // 4. buildStreamingBlocks() → 组装最终内容块
}
```

#### 5.4.3 消息同步策略

```typescript
// messageSync.ts - 核心同步函数

// 1. preserveMessageIdentity - 保留消息身份 (timestamp, uuid)
preserveMessageIdentity(prevMsg, nextMsg);

// 2. preserveLastAssistantIdentity - 保留最后assistant身份
preserveLastAssistantIdentity(prevList, nextList);

// 3. preserveStreamingAssistantContent - 保留流式内容
preserveStreamingAssistantContent(prevList, nextList, isStreamingRef, ...);

// 4. appendOptimisticMessageIfMissing - 追加乐观消息
appendOptimisticMessageIfMissing(prevList, nextList);

// 5. stripDuplicateTrailingToolMessages - 去除重复尾部
stripDuplicateTrailingToolMessages(nextList, provider);

// 6. ensureStreamingAssistantInList - 确保流式消息存在
ensureStreamingAssistantInList(prevList, resultList, isStreaming, turnId);
```

### 5.5 UI 渲染实现要点

#### 5.5.1 内容块分发模式

```typescript
// ContentBlockRenderer.tsx - 单一入口，分发到具体组件

export function ContentBlockRenderer(props: ContentBlockRendererProps) {
  switch (props.block.type) {
    case 'text':
      return <MarkdownBlock content={props.block.text} />;

    case 'thinking':
      return <ThinkingBlock
        content={props.block.thinking}
        isExpanded={props.isThinkingExpanded}
        onToggle={props.onToggleThinking}
      />;

    case 'tool_use':
      return renderToolBlock(props.block);

    case 'image':
      return <ImagePreview src={props.block.src} />;

    case 'attachment':
      return <AttachmentChip fileName={props.block.fileName} />;

    default:
      return null;
  }
}
```

#### 5.5.2 Diff 计算实现

```typescript
// 使用 LCS 算法，时间复杂度 O(mn)

function computeDiff(oldLines: string[], newLines: string[]): DiffResult {
  const m = oldLines.length;
  const n = newLines.length;

  // 1. 构建 DP 表
  const dp = Array(m + 1).fill(null).map(() => Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (oldLines[i-1] === newLines[j-1]) {
        dp[i][j] = dp[i-1][j-1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
      }
    }
  }

  // 2. 回溯生成 diff
  const lines: DiffLine[] = [];
  let i = m, j = n;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i-1] === newLines[j-1]) {
      lines.unshift({ type: 'unchanged', content: oldLines[i-1] });
      i--; j--;
    } else if (j > 0 && (i === 0 || dp[i][j-1] >= dp[i-1][j])) {
      lines.unshift({ type: 'added', content: newLines[j-1] });
      j--;
    } else {
      lines.unshift({ type: 'deleted', content: oldLines[i-1] });
      i--;
    }
  }

  return { lines, additions, deletions };
}
```

#### 5.5.3 Thinking 折叠实现

```typescript
// 折叠状态管理
const [expandedThinking, setExpandedThinking] = useState<Record<number, boolean>>({});
const [manuallyExpanded, setManuallyExpanded] = useState<Record<number, boolean>>({});

// 切换逻辑
const toggleThinking = (blockIndex: number) => {
  setExpandedThinking(prev => ({ ...prev, [blockIndex]: !prev[blockIndex] }));
  setManuallyExpanded(prev => ({ ...prev, [blockIndex]: true })); // 标记手动
};

// 流式期间自动展开最新
useEffect(() => {
  if (isStreaming) {
    const lastThinkingIndex = findLastThinkingIndex(blocks);
    setExpandedThinking(prev => ({
      ...prev,
      [lastThinkingIndex]: !manuallyExpanded[lastThinkingIndex]
    }));
  }
}, [blocks, isStreaming]);
```

### 5.6 主题变量参考

```less
// message.less - 核心样式变量

// Diff 颜色
@diff-added-bg: #d4edda;
@diff-added-accent: #28a745;
@diff-deleted-bg: #f8d7da;
@diff-deleted-accent: #dc3545;

// Thinking 块
@thinking-block-bg: var(--bg-tertiary);
@thinking-header-color: var(--text-secondary);

// 代码块
@code-block-bg: #1e1e1e;
@code-block-border: var(--border-primary);

// 工具块
@tool-block-bg: var(--bg-secondary);
@tool-header-bg: var(--bg-tertiary);
@tool-status-pending: var(--status-warning);
@tool-status-completed: var(--status-success);
@tool-status-error: var(--status-error);
```

### 5.7 性能优化要点

#### 5.7.1 后端节流策略

```java
// StreamMessageCoalescer.java

// 1. 自适应节流：基于 payload 大小
if (payload > 500_000) return 5000;  // 超大
if (payload > 200_000) return 2000;  // 大
if (payload > 100_000) return 500;   // 中
return 50;  // 正常

// 2. 心跳保活：10秒间隔
// 防止工具执行阶段无内容导致 stream stall
```

#### 5.7.2 前端渲染优化

```typescript
// 1. 流式期间轻量级渲染
if (isStreaming) {
  return renderStreamingContent(content);  // 不调用 marked.parse()
}

// 2. requestAnimationFrame 合并更新
if (isStreaming) {
  pendingUpdateJson = json;
  if (pendingUpdateRaf === null) {
    pendingUpdateRaf = requestAnimationFrame(() => {
      processUpdateMessages(pendingUpdateJson);
    });
  }
}

// 3. React.memo 缓存
export const MessageItem = memo(function MessageItem({ ... }) {
  // 内容变化时才重渲染
});

// 4. useMemo 缓存计算
const groupedBlocks = useMemo(() => groupBlocks(blocks), [blocks]);
```

### 5.8 通信桥接实现

#### 5.8.1 Java → JavaScript

```java
// JBCefCallbackHandler.java

public void callJavaScript(String functionName, String... args) {
  if (browser == null || browser.isDisposed()) return;

  StringBuilder script = new StringBuilder(functionName);
  script.append("(");
  for (int i = 0; i < args.length; i++) {
    if (i > 0) script.append(",");
    script.append("JSON.parse(").append escaped JSON).append(")");
  }
  script.append(")");

  browser.getCefBrowser().executeJavaScript(script.toString(), null, 0);
}
```

#### 5.8.2 JavaScript → Java

```typescript
// bridge.ts

export const sendToJava = (method: string, params: object) => {
  if (window.intellij) {
    window.intellij.postMessage(JSON.stringify({ method, params }));
  }
};

// 注册 Java 回调
window.onStreamStart = (data) => { /* 处理 */ };
window.onContentDelta = (delta) => { /* 处理 */ };
```

### 5.9 完整集成检查清单

```
□ 1. 供应商管理
  □ ProviderManager 核心实现
  □ 特殊供应商 ID 处理
  □ 供应商预设配置
  □ 切换流程状态机

□ 2. 流式消息
  □ StreamAdapter 标签协议
  □ MessageCallback 接口
  □ StreamMessageCoalescer 节流
  □ 心跳机制

□ 3. 前端处理
  □ useStreamingMessages Hook
  □ streamingCallbacks 注册
  □ messageCallbacks 注册
  □ messageSync 同步策略

□ 4. UI 渲染
  □ ContentBlockRenderer 分发
  □ MarkdownBlock 渲染
  □ ThinkingBlock 折叠
  □ EditToolBlock Diff

□ 5. 通信桥接
  □ JBCefHandler
  □ 窗口回调注册
  □ 双向通信

□ 6. 性能优化
  □ 自适应节流
  □ rAF 合并更新
  □ React.memo 缓存
```

---

## 附录 A: 文件速查表

### 后端 (Java)

| 文件 | 职责 |
|------|------|
| `ClaudeStreamAdapter.java` | 标签协议解析 |
| `StreamMessageCoalescer.java` | 消息聚合与节流 |
| `ClaudeSDKBridge.java` | SDK 桥接与调用 |
| `DaemonBridge.java` | 长连接 Daemon 管理 |
| `ProviderManager.java` | 供应商 CRUD |
| `ClaudeSettingsManager.java` | 设置持久化 |

### 前端 (TypeScript/React)

| 文件 | 职责 |
|------|------|
| `useStreamingMessages.ts` | 流式状态管理 |
| `streamingCallbacks.ts` | 流式回调处理 |
| `messageCallbacks.ts` | 消息回调处理 |
| `messageSync.ts` | 消息同步工具 |
| `messageUtils.ts` | 消息解析工具 |
| `ContentBlockRenderer.tsx` | 内容块分发 |
| `MarkdownBlock.tsx` | Markdown 渲染 |
| `EditToolBlock.tsx` | 编辑工具 + Diff |

---

## 附录 B: 参考资料

- [IntelliJ Platform SDK 文档](https://plugins.jetbrains.com/docs/intellij/)
- [JCEF (Java Chromium Embedded Framework)](https://plugins.jetbrains.com/docs/intellij/jcef.html)
- [React 文档](https://react.dev/)
- [highlight.js](https://highlightjs.org/)
- [marked](https://marked.js.org/)
- [DOMPurify](https://github.com/cure53/DOMPurify)
