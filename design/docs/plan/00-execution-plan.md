# CC Assistant 交互优化执行计划

**文档版本**: v1.0
**基于**: 用户需求（2026-04-10）
**目标**: 实施交互优化（布局重构 + 模型配置透明化 + Chat 增强）
**预计工期**: 约 4 个工作日

---

## 执行摘要

| Phase | 内容 | 工期 | 交付物 |
|-------|------|------|--------|
| 0 | PRD Gap 梳理 | - | 本文档 |
| 1 | 基础设施 | 1 天 | chatConfigStore + SdkOptions + ModelConfig + CefBrowserPanel handlers + java-bridge |
| 2 | 模型配置透明化 | 0.5 天 | ModelConfigPanel V2 + JsonConfigEditor + ProviderSelector V2 |
| 3 | ChatToolbar | 1 天 | ChatToolbar + ModeSwitcher + AgentSelector + Toggles |
| 4 | TaskStatusBar | 0.5 天 | TaskStatusBar + handleGetTaskStatus |
| 5 | SlashCommandPalette | 0.5 天 | SlashCommandPalette + handleExecuteSlashCommand |
| 6 | 布局优化 | 0.5 天 | AppLayout 收缩 + 右上角工具下拉 |

**验收标准**：每个 Phase 完成后，必须同时满足：
1. `./gradlew compileKotlin` 成功
2. `cd webview && npm run build` 成功
3. 对应的功能验收条目全部通过

---

## Phase 0: PRD Gap 梳理

### 0.1 用户需求确认

| 需求项 | 确认内容 |
|--------|----------|
| 布局 | 左侧 sidebar 收缩为 48px 图标模式 + 右上角工具下拉按钮 |
| Slash 命令 | 完整集：/compact, /clear, /model, /mode, /retry, /export |
| Per-mode 模型默认值 | Anthropic: sonnet=claude-sonnet-4-20250514, opus=claude-opus-4-20250514, max=claude-3-5-haiku-20250514 |
| | GLM: opus=glm-5, max=glm-4.7, sonnet=glm-4.7 |
| | MiniMax: opus/max/sonnet=MiniMax-M2.7 |
| 模型配置透明化 | 所有字段（url/apiKey/model）明文显示，支持 JSON 一键导入/导出 |
| Chat 增强 | Agent 选择、模式切换、Thinking 开关、流式开关、任务状态栏、命令面板 |

---

## Phase 1: 基础设施

**目标**：建立前后端通信基础（chatConfigStore、新增 backend handlers、新增 java-bridge 方法）

**验收标准**：
- [ ] `chatConfigStore.ts` 可正常 import，store 中有 conversationMode/currentAgentId/streamingEnabled/thinkingEnabled/thinkingBudgetTokens
- [ ] `java-bridge.ts` 可调用 `getChatConfig()` 和 `updateChatConfig()`，返回正确类型
- [ ] CefBrowserPanel 的 `handleGetChatConfig` 和 `handleUpdateChatConfig` 可正确响应（通过日志或断点验证）
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build`（webview）成功

### Task 1.1: 新建 chatConfigStore

**文件**: `webview/src/shared/stores/chatConfigStore.ts`（新建）

```typescript
// 初始状态
const DEFAULT_CHAT_CONFIG = {
  conversationMode: 'AUTO' as 'AUTO' | 'THINKING' | 'PLANNING',
  currentAgentId: null as string | null,
  streamingEnabled: true,
  thinkingEnabled: false,
  thinkingBudgetTokens: null as number | null,
};
```

**验收标准**：
- [ ] store 包含上述 5 个字段
- [ ] 有 `setConversationMode`, `setCurrentAgent`, `setStreamingEnabled`, `setThinkingEnabled`, `setThinkingBudgetTokens` 方法
- [ ] 有 `loadChatConfig()` 方法，调用 `javaBridge.getChatConfig()` 并覆盖状态

---

### Task 1.2: SdkOptions 新增 thinkingBudget 字段

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/adaptation/sdk/SdkConfigBuilder.kt`

在 `data class SdkOptions` 中新增：
```kotlin
val thinkingBudgetTokens: Int? = null,
val includeThinking: Boolean = false,
```

在 `buildCommand()` 中新增 CLI 参数生成：
```kotlin
options.thinkingBudgetTokens?.let {
    command.add("--thinking-budget")
    command.add(it.toString())
}
if (options.includeThinking) {
    command.add("--include-thinking")
}
```

**验收标准**：
- [ ] `SdkOptions` 包含 `thinkingBudgetTokens` 和 `includeThinking` 字段
- [ ] `buildCommand()` 正确生成 `--thinking-budget <n>` 和 `--include-thinking` CLI 参数
- [ ] `./gradlew compileKotlin` 成功

---

### Task 1.3: ModelConfig 新增 per-mode 字段

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/model/config/ModelConfig.kt`

在 `data class ModelConfig` 中新增：
```kotlin
// Per-mode 模型映射（按供应商不同有不同的默认值）
val sonnetModel: String? = null,     // AUTO 模式模型
val opusModel: String? = null,        // THINKING 模式模型
val maxModel: String? = null,         // PLANNING 模式模型
val thinkingBudgetTokens: Int? = null,
```

同时更新 `fromJson()` 和 `toJson()` 序列化/反序列化逻辑。

**验收标准**：
- [ ] ModelConfig 包含 sonnetModel/opusModel/maxModel/thinkingBudgetTokens
- [ ] `fromJson()` 正确解析新字段
- [ ] `toJson()` 正确序列化新字段
- [ ] `./gradlew compileKotlin` 成功

---

### Task 1.4: CefBrowserPanel 新增 handler

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/browser/CefBrowserPanel.kt`

新增 3 个 handler 方法：

| 方法 | 职责 | 返回值 |
|------|------|--------|
| `handleGetChatConfig` | 读取当前 chat 配置（mode/agent/streaming/thinking） | `{ conversationMode, currentAgentId, streamingEnabled, thinkingEnabled, thinkingBudgetTokens }` |
| `handleUpdateChatConfig` | 更新 chat 配置（内存，不持久化） | `{ success: true }` |
| `handleExecuteSlashCommand` | 执行 slash 命令 | 命令执行结果 |
| `handleGetTaskStatus` | 获取任务状态（见 Phase 4） | `{ tasks[], diffRecords[], activeSubagents[] }` |

在 `handleJsRequest()` 的 action 路由中注册：
```kotlin
"getChatConfig" -> handleGetChatConfig(queryId, params)
"updateChatConfig" -> handleUpdateChatConfig(queryId, params)
"executeSlashCommand" -> handleExecuteSlashCommand(queryId, params)
"getTaskStatus" -> handleGetTaskStatus(queryId, params)
```

**验收标准**：
- [ ] 4 个 handler 方法存在且签名正确
- [ ] `handleJsRequest` 路由表包含上述 4 个 action
- [ ] `./gradlew compileKotlin` 成功

---

### Task 1.5: java-bridge 新增方法

**文件**: `webview/src/lib/java-bridge.ts`

在 `JavaBridge` 类中新增 4 个方法：

```typescript
async getChatConfig(): Promise<ChatConfig> {
  return this.invoke('getChatConfig', {});
}

async updateChatConfig(config: Partial<ChatConfig>): Promise<void> {
  return this.invoke('updateChatConfig', { config });
}

async getTaskStatus(): Promise<TaskStatusResponse> {
  return this.invoke('getTaskStatus', {});
}

async executeSlashCommand(command: string): Promise<any> {
  return this.invoke('executeSlashCommand', { command });
}
```

同步更新 `webview/src/shared/types/bridge.ts` 中的 `JavaBackendAPI` 接口。

**验收标准**：
- [ ] `java-bridge.ts` 包含 `getChatConfig/updateChatConfig/getTaskStatus/executeSlashCommand`
- [ ] `bridge.ts` 接口已更新
- [ ] `npm run build` 成功

---

## Phase 2: 模型配置透明化

**目标**：ModelConfigPanel V2 支持 JSON 一键配置 + per-mode 模型映射

**验收标准**：
- [ ] 可以粘贴 JSON 并导入完整配置
- [ ] 可以导出当前配置为 JSON
- [ ] ProviderSelector 显示 per-mode 模型下拉选择
- [ ] 不同 provider 切换时，per-mode 默认值正确填充
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

### Task 2.1: ProviderSelector V2（per-mode 选择）

**文件**: `webview/src/features/model/components/ProviderSelector.tsx`

修改 `ProviderSelector` 组件，新增 per-mode 模型映射选择：

```typescript
// 每个 Provider 包含 per-mode 默认值
export interface Provider {
  id: string;
  name: string;
  models: string[];
  defaultModel: string;
  // 新增
  sonnetDefault?: string;  // AUTO 模式默认
  opusDefault?: string;    // THINKING 模式默认
  maxDefault?: string;     // PLANNING 模式默认
}
```

**默认值映射**：
```typescript
const PROVIDERS: Provider[] = [
  { id: 'anthropic', name: 'Anthropic', sonnetDefault: 'claude-sonnet-4-20250514', opusDefault: 'claude-opus-4-20250514', maxDefault: 'claude-3-5-haiku-20250514', ... },
  { id: 'glm', name: '智谱 GLM', sonnetDefault: 'glm-4.7', opusDefault: 'glm-5', maxDefault: 'glm-4.7', ... },
  { id: 'minimax', name: 'MiniMax', sonnetDefault: 'MiniMax-M2.7', opusDefault: 'MiniMax-M2.7', maxDefault: 'MiniMax-M2.7', ... },
  // ...
];
```

**验收标准**：
- [ ] Provider 接口包含 sonnetDefault/opusDefault/maxDefault
- [ ] ProviderSelector 显示 3 个下拉框（sonnet/opus/max 模型选择）
- [ ] 切换 provider 时，3 个下拉框自动填充对应默认值
- [ ] `npm run build` 成功

---

### Task 2.2: ModelConfigPanel V2

**文件**: `webview/src/features/model/components/ModelConfigPanel.tsx`（重写）

重写 `ModelConfigPanel`，新增：

1. **JSON 编辑区**（折叠/展开）
   - 文本框：粘贴 JSON 导入配置
   - "导入" 按钮：解析 JSON 并填充表单
   - "导出" 按钮：把当前表单序列化为 JSON 并显示

2. **Per-mode 模型选择**（使用 ProviderSelector V2 的 3 下拉框）

3. **Thinking Budget 配置**
   - Toggle 开关：是否启用 extended thinking
   - 数值输入：thinkingBudgetTokens（启用时可见）

4. **明文显示所有字段**（apiKey 可切换显示/隐藏）

**验收标准**：
- [ ] JSON 粘贴导入后，表单字段正确填充
- [ ] 导出 JSON 包含 sonnetModel/opusModel/maxModel/thinkingBudgetTokens
- [ ] Per-mode 3 下拉框正确显示和切换
- [ ] Thinking budget 开关和数值输入联动
- [ ] `npm run build` 成功

---

### Task 2.3: JsonConfigEditor 组件

**文件**: `webview/src/features/model/components/JsonConfigEditor.tsx`（新建）

```typescript
interface JsonConfigEditorProps {
  value: string;              // JSON 字符串
  onImport: (parsed: object) => void;  // 导入回调
  onExport: () => void;        // 导出当前表单
  error?: string | null;       // 解析错误提示
}
```

**UI**：
- 顶部：标题 "JSON 配置"
- 中部：`<textarea>` 可编辑 JSON
- 底部：导入按钮 + 导出按钮 + 错误提示

**验收标准**：
- [ ] 组件可正确 import/export JSON
- [ ] 解析失败时显示友好错误信息
- [ ] `npm run build` 成功

---

## Phase 3: ChatToolbar

**目标**：ChatView 顶部添加工具栏（ModeSwitcher + AgentSelector + Toggles）

**验收标准**：
- [ ] ChatView 顶部显示 ModeSwitcher（AUTO/THINKING/PLANNING 3 态切换）
- [ ] ChatView 顶部显示 AgentSelector 下拉（从 agentsStore 读取）
- [ ] ChatView 顶部显示 ThinkingToggle（开关）
- [ ] ChatView 顶部显示 StreamingToggle（开关）
- [ ] 点击切换后正确调用 `javaBridge.updateChatConfig()`
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

### Task 3.1: ModeSwitcher 组件

**文件**: `webview/src/main/components/ModeSwitcher.tsx`（新建）

```typescript
// 3 个按钮，AUTO / THINKING / PLANNING
// 当前选中项高亮（bg-primary）
// 点击时调用 chatConfigStore.setConversationMode(mode)
```

**验收标准**：
- [ ] 显示 3 个切换按钮
- [ ] 当前选中模式高亮
- [ ] 点击切换后调用 `setConversationMode` 并同步到 backend

---

### Task 3.2: AgentSelector 组件

**文件**: `webview/src/main/components/AgentSelector.tsx`（新建）

```typescript
// 下拉选择框，从 agentsStore.agents 读取
// 显示 agent 名称 + mode badge（CAUTIOUS/BALANCED/AGGRESSIVE）
// 包含"无"选项（currentAgentId = null）
// 点击时调用 chatConfigStore.setCurrentAgent(agentId)
```

**验收标准**：
- [ ] 下拉显示所有 agent（名称 + mode badge）
- [ ] "无" 选项在列表首位
- [ ] 选择后正确调用 `setCurrentAgent` 并同步到 backend

---

### Task 3.3: ThinkingToggle 组件

**文件**: `webview/src/main/components/ThinkingToggle.tsx`（新建）

```typescript
// Toggle 开关
// 开启时：chatConfigStore.setThinkingEnabled(true)
// 关闭时：chatConfigStore.setThinkingEnabled(false)
// 开启后显示 thinkingBudgetTokens 数值输入
```

**验收标准**：
- [ ] Toggle 开关状态正确
- [ ] 开启时显示 thinkingBudgetTokens 数值输入（默认 1024）
- [ ] 值变化时正确调用 `setThinkingBudgetTokens`

---

### Task 3.4: StreamingToggle 组件

**文件**: `webview/src/main/components/StreamingToggle.tsx`（新建）

```typescript
// Toggle 开关
// 开启/关闭流式输出
// 点击时调用 chatConfigStore.setStreamingEnabled(enabled)
```

**验收标准**：
- [ ] Toggle 开关状态正确
- [ ] 点击切换后调用 `setStreamingEnabled`

---

### Task 3.5: ChatToolbar 整合

**文件**: `webview/src/main/components/ChatToolbar.tsx`（新建）

整合 4 个子组件为一横行工具栏：

```
[ModeSwitcher] | [AgentSelector] | [ThinkingToggle] [BudgetInput] | [StreamingToggle]
```

布局：flex row，gap-2，p-2 border-b

**验收标准**：
- [ ] 工具栏横跨 ChatView 顶部
- [ ] 4 个子组件正确渲染
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

---

## Phase 4: TaskStatusBar

**目标**：ChatView 底部显示任务状态栏（任务进度 + subagent 状态 + diff 记录）

**验收标准**：
- [ ] 任务进度环形指示器正确显示
- [ ] 当前步骤名称显示
- [ ] subagent 状态列表正确显示
- [ ] diff 记录列表正确显示（文件名 + 状态）
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

### Task 4.1: handleGetTaskStatus 后端实现

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/browser/CefBrowserPanel.kt`

实现 `handleGetTaskStatus`，返回：

```kotlin
data class TaskStatusResponse(
    val tasks: List<TaskProgress>,           // TaskProgressTracker 的所有活跃任务
    val diffRecords: List<DiffRecord>,        // DiffRecord 列表（见 Task 4.3）
    val activeSubagents: List<SubagentStatus> // AgentExecutor 中正在执行的 agent
)

data class SubagentStatus(
    val agentId: String,
    val agentName: String,
    val task: String,
    val status: String  // RUNNING/COMPLETED/FAILED
)
```

从以下数据源聚合：
- `TaskProgressTracker.getActiveTasks()`
- `AgentExecutor.activeExecutions`

**验收标准**：
- [ ] `handleGetTaskStatus` 返回正确结构
- [ ] `./gradlew compileKotlin` 成功

---

### Task 4.2: DiffRecord 模型

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/model/diff/DiffRecord.kt`（新建）

```kotlin
data class DiffRecord(
    val filePath: String,
    val status: DiffStatus,  // ADDED / MODIFIED / DELETED
    val hunks: Int,           // 变更块数量
    val timestamp: Long
)

enum class DiffStatus { ADDED, MODIFIED, DELETED }
```

**验收标准**：
- [ ] DiffRecord 和 DiffStatus 正确声明

---

### Task 4.3: StreamingOutputEngine Diff 解析

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/application/streaming/StreamingOutputEngine.kt`

在 `StreamingOutputEngine` 中，解析 Claude CLI NDJSON 输出中的 diff 信息（通常为 `tool_use` 类型中的 `content` 包含 `diff` 或 `name` 字段为文件路径）。

解析后存入 `List<DiffRecord>`，并通过 `handleGetTaskStatus` 返回。

**验收标准**：
- [ ] NDJSON 中 `tool_use` 的 diff 信息被正确解析为 DiffRecord
- [ ] DiffRecord 存入内存列表
- [ ] `./gradlew compileKotlin` 成功

---

### Task 4.4: TaskStatusBar 组件

**文件**: `webview/src/main/components/TaskStatusBar.tsx`（新建）

```typescript
// 3 个区域（可折叠）：
// 1. 任务进度：环形进度 + "N/M 步骤" + 当前步骤名称
// 2. Subagent 状态：列表显示（agent 名 + 任务 + 状态 badge）
// 3. Diff 记录：列表显示（文件路径 + 状态标签 + 时间）
// 轮询：每 2 秒调用 javaBridge.getTaskStatus() 刷新
```

**验收标准**：
- [ ] 3 个区域正确渲染
- [ ] 轮询刷新正常工作
- [ ] 无数据时显示"暂无任务"等空状态
- [ ] `npm run build` 成功

---

## Phase 5: SlashCommandPalette

**目标**：ChatInput 输入 `/` 时弹出命令面板

**验收标准**：
- [ ] ChatInput 输入 `/` 后自动显示浮动面板
- [ ] 面板列出 6 个命令（/compact, /clear, /model, /mode, /retry, /export）
- [ ] 支持键盘上下选择 + Enter 执行
- [ ] Escape 或点击外部关闭面板
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

### Task 5.1: SlashCommandPalette 组件

**文件**: `webview/src/main/components/SlashCommandPalette.tsx`（新建）

```typescript
interface SlashCommand {
  command: string;      // '/compact'
  description: string;  // '压缩上下文'
  action: () => void;   // 执行函数
}

const COMMANDS: SlashCommand[] = [
  { command: '/compact', description: '压缩上下文', action: () => javaBridge.executeSlashCommand('/compact') },
  { command: '/clear', description: '清空当前会话', action: () => javaBridge.executeSlashCommand('/clear') },
  { command: '/model', description: '切换模型（后续参数）', action: () => {} },
  { command: '/mode', description: '切换模式（auto/thinking/planning）', action: () => {} },
  { command: '/retry', description: '重试上一条消息', action: () => {} },
  { command: '/export', description: '导出会话', action: () => {} },
];
```

**UI**：绝对定位浮层，`<input>` 过滤 + 命令列表，键盘导航

**验收标准**：
- [ ] `/` 触发显示面板
- [ ] 键盘上下选择 + Enter 执行
- [ ] Escape/点击外部关闭
- [ ] `npm run build` 成功

---

### Task 5.2: ChatInput 集成

**文件**: `webview/src/features/chat/components/ChatInput.tsx`

修改 `ChatInput`：
- 监听 text 输入，当 text 以 `/` 开头时，设置 `showSlashPalette: true`
- 当 `showSlashPalette` 为 true 时，渲染 `<SlashCommandPalette>`
- 选择命令后，清空输入框，调用命令 action

**验收标准**：
- [ ] 输入 `/` 自动弹出面板
- [ ] 命令执行后正确行为（/compact 压缩，/clear 清空等）
- [ ] `npm run build` 成功

---

### Task 5.3: handleExecuteSlashCommand 后端实现

**文件**: `src/main/kotlin/com/github/xingzhewa/ccgui/browser/CefBrowserPanel.kt`

实现 `handleExecuteSlashCommand`：

| 命令 | 行为 |
|------|------|
| `/compact` | 调用 `ContextManager.compact(sessionId)` |
| `/clear` | 调用 `SessionManager.clearSession(sessionId)` |
| `/model <name>` | 更新 ModelConfig.model，临时生效 |
| `/mode <auto\|thinking\|planning>` | 更新 chatConfig 的 conversationMode |
| `/retry` | 重新发送上一条用户消息 |
| `/export` | 调用 `SessionManager.exportSession(sessionId)` |

**验收标准**：
- [ ] 所有 6 个命令正确执行
- [ ] `./gradlew compileKotlin` 成功

---

## Phase 6: 布局优化

**目标**：AppLayout sidebar 收缩为 48px + 右上角工具下拉按钮

**验收标准**：
- [ ] Sidebar 收缩为 48px，只显示图标
- [ ] 右上角显示工具按钮（齿轮/工具图标）
- [ ] 点击按钮显示下拉菜单（Agent 列表入口、工具栏设置入口）
- [ ] SettingsView 新增"工具栏设置"Tab
- [ ] `./gradlew compileKotlin` 成功
- [ ] `npm run build` 成功

### Task 6.1: AppLayout 收缩

**文件**: `webview/src/main/components/AppLayout.tsx`

修改 sidebar 逻辑：
```typescript
// 原来: sidebarOpen ? 'w-[280px]' : 'w-[60px]'
// 现在: 始终 'w-[48px]'（只显示图标）
// 新增: 右上角工具按钮
```

右上角下拉菜单包含：
- "Agent 管理" → 跳转 /agents
- "工具栏设置" → 跳转 /settings（定位到工具栏 Tab）
- "会话历史" → 跳转 /history

**验收标准**：
- [ ] sidebar 宽度固定 48px
- [ ] 右上角工具按钮存在
- [ ] 点击按钮显示下拉菜单
- [ ] `npm run build` 成功

---

### Task 6.2: SettingsView 新增工具栏设置 Tab

**文件**: `webview/src/main/pages/SettingsView.tsx`

新增 Tab 页面"工具栏"：
- Sidebar 显示模式（仅图标 / 展开）
- 是否显示会话标签栏
- 快捷键配置（预留）
- 通知设置（预留）

**验收标准**：
- [ ] SettingsView 包含工具栏 Tab
- [ ] Tab 内容完整
- [ ] `npm run build` 成功

---

## 依赖关系图

```
Phase 1（基础设施）
  └── 所有后续 Phase 依赖

Phase 2（模型配置）
  └── 依赖 Phase 1（chatConfigStore + ModelConfig）

Phase 3（ChatToolbar）
  └── 依赖 Phase 1（chatConfigStore + java-bridge）

Phase 4（TaskStatusBar）
  └── 依赖 Phase 1（handleGetTaskStatus + java-bridge.getTaskStatus）

Phase 5（SlashCommandPalette）
  └── 依赖 Phase 1（handleExecuteSlashCommand + java-bridge.executeSlashCommand）
  └── 依赖 Phase 3（AgentSelector 组件）

Phase 6（布局优化）
  └── 无依赖，可独立实施
```

---

## 技术风险与应对

| 风险 | 等级 | 应对 |
|------|------|------|
| JCEF 事件监听器内存泄漏 | 高 | useEffect return 必须返回 unsubscribe |
| Thinking Budget CLI 版本兼容性 | 中 | CLI 版本检测，不支持则灰度按钮 |
| Diff 解析依赖 CLI 输出格式 | 中 | 正则 + fallback，日志记录解析失败 |
| 任务进度更新频率过高 | 低 | Debounce 100ms，批量更新 |
| 多语言环境文本过长撑开 UI | 低 | TextOverflow ellipsis，Tooltip 显示完整文本 |

---

## 关键文件索引

| 文件 | 作用 |
|------|------|
| `src/main/kotlin/.../bridge/BridgeManager.kt` | Phase 1+ — 消息发送核心，需改造支持 conversationMode + thinkingBudget |
| `src/main/kotlin/.../adaptation/sdk/SdkConfigBuilder.kt` | Phase 1 — SdkOptions 新增字段 |
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | Phase 1/4/5 — 前端→后端路由，需新增 4 个 handler |
| `src/main/kotlin/.../model/config/ModelConfig.kt` | Phase 1/2 — 模型配置，新增 per-mode 字段 |
| `src/main/kotlin/.../application/streaming/StreamingOutputEngine.kt` | Phase 4 — Diff 解析 |
| `src/main/kotlin/.../model/diff/DiffRecord.kt` | Phase 4 — DiffRecord 模型（新建） |
| `webview/src/lib/java-bridge.ts` | Phase 1/3/4/5 — 前后端通信，需新增 4 个方法 |
| `webview/src/shared/types/bridge.ts` | Phase 1 — JavaBackendAPI 接口更新 |
| `webview/src/shared/stores/chatConfigStore.ts` | Phase 1/3 — Chat 配置状态（新建） |
| `webview/src/main/pages/ChatView.tsx` | Phase 3/4 — 集成 ChatToolbar + TaskStatusBar |
| `webview/src/main/App.tsx` | Phase 1 — 注册新事件监听 |
| `webview/src/main/components/AppLayout.tsx` | Phase 6 — Sidebar 收缩 + 工具按钮 |
| `webview/src/main/components/ChatToolbar.tsx` | Phase 3 — Chat 工具栏（新建） |
| `webview/src/main/components/SlashCommandPalette.tsx` | Phase 5 — 命令面板（新建） |
| `webview/src/main/components/TaskStatusBar.tsx` | Phase 4 — 任务状态栏（新建） |
| `webview/src/features/model/components/ModelConfigPanel.tsx` | Phase 2 — 重写为 V2 |
| `webview/src/features/model/components/JsonConfigEditor.tsx` | Phase 2 — JSON 编辑器（新建） |
| `webview/src/features/model/components/ProviderSelector.tsx` | Phase 2 — V2 per-mode 选择 |

---

*计划版本：v1.0 | 编制日期：2026-04-10 | 预计工期：4 天 | 基于：用户需求确认*
