# ClaudeCodeJet 后端开发指引

**文档版本**: 1.0
**创建日期**: 2026-04-08
**适用范围**: 后端 Kotlin 代码开发

---

## 1. 文档体系总览

本目录 (`design/docs/backend/`) 包含 8 份文档，构成完整的后端开发方案：

```
00-overview.md          ← 你正在阅读的总览（架构、阶段划分、规范）
01-phase1-foundation.md ← Phase 1: 数据模型 + 基础设施 (38文件)
02-phase2-adaptation.md ← Phase 2: 通信适配层 (9文件，3个已废弃)
02-phase2.5-claude-code-sdk.md ← ★ Phase 2.5: SDK集成 (6文件，核心关键路径)
03-phase3-core-services.md ← Phase 3: 核心应用服务 (10文件)
04-phase4-features.md  ← Phase 4: 功能模块 (6文件)
05-phase5-ecosystem.md ← Phase 5: 生态集成 (6文件)
06-phase6-optimization.md ← Phase 6: 性能优化+测试 (7+25文件)
07-development-guide.md ← 本文档：开发指引
```

---

## 2. 推荐阅读顺序

### 第一遍：理解全貌（约30分钟）

| 顺序 | 文档 | 重点阅读内容 | 目标 |
|------|------|-------------|------|
| 1 | `00-overview.md` | 全部。重点是 §2 架构图、§3 阶段划分、§4 依赖关系 | 理解整体架构和7个阶段的关系 |
| 2 | `02-phase2.5-claude-code-sdk.md` §1 | 概览和数据流图 | 理解 **SDK集成是核心关键路径** |
| 3 | 本文档 §3-§5 | 核心概念和开发路径 | 建立开发全景图 |

### 第二遍：深入设计（约2小时）

| 顺序 | 文档 | 重点阅读内容 | 目标 |
|------|------|-------------|------|
| 4 | `01-phase1-foundation.md` | §2 数据模型、§3 基础设施 | 掌握所有共享类型定义 |
| 5 | `02-phase2.5-claude-code-sdk.md` | 全部。重点是 `ClaudeCodeClient` 和 `StreamJsonParser` | 掌握SDK通信核心 |
| 6 | `03-phase3-core-services.md` 顶部修订 + `ChatOrchestrator` | SDK适配后的编排器设计 | 理解消息处理核心链路 |
| 7 | `02-phase2-adaptation.md` 顶部修订说明 | 确认哪些组件保留、哪些废弃 | 避免使用废弃代码 |

### 第三遍：功能模块（按需，约1.5小时）

| 顺序 | 文档 | 按需阅读 |
|------|------|---------|
| 8 | `04-phase4-features.md` | 顶部修订 + 你负责的功能模块 |
| 9 | `05-phase5-ecosystem.md` | 顶部修订 + 你负责的功能模块 |
| 10 | `06-phase6-optimization.md` | 顶部修订 + 性能指标和测试清单 |

---

## 3. 核心概念速查

### 3.1 插件定位

```
Claude Code CLI (命令行工具) ←→ ClaudeCodeJet 插件 (JetBrains IDE GUI)
```

本插件是 Claude Code 的 **JetBrains IDE 图形界面前端**。所有AI能力通过 Claude Code SDK（CLI子进程）获取，**不直接调用任何AI供应商的REST API**。

### 3.2 核心数据流

```
用户输入
  → ChatOrchestrator (应用层编排)
    → ClaudeCodeClient (SDK客户端)
      → ProcessBuilder("claude", "-p", prompt, "--output-format", "stream-json")
        → CLI子进程执行
          → stdout 输出 NDJSON (stream-json协议)
            → StreamJsonParser 逐行解析
              → SdkMessage 类型分发:
                  - SdkInitMessage     → 注册会话
                  - SdkAssistantMessage → 推送到JCEF前端
                  - SdkResultMessage   → 完成通知
    → StreamingOutputEngine → JCEF (browserPanel.sendToJavaScript)
      → React前端渲染
```

### 3.3 关键类职责

| 类 | 所在Phase | 一句话描述 |
|----|----------|-----------|
| `ClaudeCodeClient` | 2.5 | 调用 `claude` CLI 子进程的唯一入口 |
| `StreamJsonParser` | 2.5 | 将 CLI 的 NDJSON 输出解析为类型化消息 |
| `SdkConfigBuilder` | 2.5 | 构建 CLI 命令参数和 MCP 配置文件 |
| `SdkSessionManager` | 2.5 | 管理应用层session ↔ SDK session_id 映射 |
| `ChatOrchestrator` | 3 | 消息编排中枢，连接 Session/SDK/Streaming |
| `SessionManager` | 3 | 多会话CRUD、切换、持久化 |
| `StreamingOutputEngine` | 3 | 将流式数据桥接到 JCEF |
| `BridgeManager` | 2.5 | 兼容层：委托给 ClaudeCodeClient，保持与 MyToolWindowFactory 接口一致 |
| `CefBrowserPanel` | 1 | JCEF 封装，提供 Java→JS 和 JS→Java 双向通信 |
| `EventBus` | 1 | 模块间解耦的事件总线 |

### 3.4 SDK CLI 参数速查

| 参数 | 作用 | 对应 SdkOptions 字段 |
|------|------|---------------------|
| `-p <prompt>` | 发送提示（必选） | sendMessage的prompt参数 |
| `--output-format stream-json` | 输出格式（固定） | 常量，不由Options控制 |
| `--resume <sessionId>` | 恢复已有会话 | `resumeSessionId` |
| `--continue` | 继续最近对话 | `continueRecent` |
| `--model <modelId>` | 指定模型 | `model` |
| `--system-prompt <text>` | 系统提示 | `systemPrompt` |
| `--mcp-config <file>` | MCP服务器配置 | `mcpConfig`（自动生成临时文件） |
| `--allowedTools <list>` | 限制可用工具 | `allowedTools` |
| `--max-turns <n>` | 最大对话轮次 | `maxTurns` |

---

## 4. 开发前必读清单

在写任何代码之前，确认你已经：

- [ ] **理解SDK集成模型**：读通 `02-phase2.5-claude-code-sdk.md` 的 `ClaudeCodeClient.kt` 和 `StreamJsonParser.kt`
- [ ] **理解JCEF双向通信**：读通 `01-phase1-foundation.md` 的 `CefBrowserPanel.kt`（Java→JS via `sendToJavaScript`，JS→Java via `JBCefJSQuery`）
- [ ] **理解分层职责**：
  - `model/` = 纯数据类，无业务逻辑
  - `infrastructure/` = 通用基础设施（EventBus、Storage、Cache）
  - `adaptation/sdk/` = Claude Code SDK 通信
  - `application/` = 业务逻辑编排
  - `browser/` + `config/` = IDE集成
- [ ] **确认环境**：`claude` CLI 已安装且 `claude --version` 可执行
- [ ] **确认废弃组件**：不要实现 `AnthropicProvider`、`OpenAIProvider`、`DeepSeekProvider`、`HttpClientPool`

### 关键约束

1. **禁止直接调用 AI 供应商 REST API** — 一切通过 `ClaudeCodeClient`
2. **禁止阻塞 EDT** — 所有异步操作使用 `CoroutineScope(Dispatchers.IO)`
3. **禁止使用 `GlobalScope`** — 使用 `createDisposableScope(parent)` 或项目级 scope
4. **服务必须注册** — `@Service(Service.Level.PROJECT)` 或在 `plugin.xml` 中注册
5. **资源必须释放** — 所有持有资源（Process、Browser、CoroutineScope）的类实现 `Disposable`

---

## 5. 建议开发路径

### 路径总览

```
Phase 1 (基础设施)
  ↓ 产出：可编译的基础库
Phase 2 (通信基础) ← 只实现保留的组件
  ↓ 产出：StreamCallback、ProcessPool、VersionDetector
Phase 2.5 (SDK集成) ★ 核心关键路径
  ↓ 产出：ClaudeCodeClient可用，能发送消息并接收流式回复
Phase 3 (核心服务)
  ↓ 产出：完整聊天流程可用
Phase 4 (功能模块)
  ↓ 产出：交互增强、多模态、任务追踪
Phase 5 (生态集成)
  ↓ 产出：Skills/Agents/MCP 管理
Phase 6 (优化测试)
  ↓ 产出：性能达标、测试覆盖 > 80%
```

### 里程碑验证点

| 里程碑 | 验证标准 | 涉及Phase |
|--------|---------|----------|
| **M1: 可编译** | `./gradlew build` 通过，所有Phase 1文件编译无错 | Phase 1 |
| **M2: 可通信** | `ClaudeCodeClient.sendMessage("hello")` 收到CLI回复 | Phase 1 + 2 + 2.5 |
| **M3: 可聊天** | ToolWindow中输入消息，收到流式回复 | Phase 1-3 |
| **M4: 功能完整** | 交互式请求、任务追踪、提示优化可用 | Phase 1-4 |
| **M5: 生态就绪** | Skills/Agents/MCP配置管理可用 | Phase 1-5 |
| **M6: 质量达标** | 性能指标达标、测试覆盖 > 80% | Phase 1-6 |

### Phase 1 实现顺序建议

Phase 1 有 38 个文件，建议按以下顺序实现：

```
第1批（编译基础）:
  util/logger.kt → util/JsonUtils.kt → util/IdGenerator.kt
  （这3个文件是所有其他文件的前置依赖）

第2批（数据模型）:
  model/message/MessageRole.kt → ContentPart.kt → ChatMessage.kt
  model/session/SessionType.kt → SessionContext.kt → ChatSession.kt
  model/config/*.kt (ThemeConfig, ModelConfig, ConversationMode, AppConfig)
  model/interaction/*.kt
  model/task/*.kt
  model/provider/*.kt (AIProvider接口, ChatRequest, ChatResponse, ChatChunk, TokenUsage)
  model/skill/*.kt, model/agent/*.kt, model/mcp/*.kt

第3批（基础设施）:
  infrastructure/eventbus/Event.kt → Events.kt → EventBus.kt
  infrastructure/error/PluginExceptions.kt → ErrorRecoveryManager.kt
  infrastructure/storage/SecureStorage.kt → ConfigStorage.kt → SessionStorage.kt
  infrastructure/state/StateManager.kt
  infrastructure/cache/CacheManager.kt

第4批（IDE集成）:
  browser/CefBrowserPanel.kt
  config/CCGuiConfig.kt
```

### Phase 2.5 实现顺序建议

```
第1天: SdkMessageTypes.kt (消息类型定义)
第2天: StreamJsonParser.kt (协议解析器) + 单元测试
第3天: SdkConfigBuilder.kt (配置构建器)
第4-5天: ClaudeCodeClient.kt (核心客户端) — 这是最关键的文件
第6天: SdkSessionManager.kt + 重构 BridgeManager.kt
第7天: SdkPermissionHandler.kt
第8天: 端到端集成测试
```

---

## 6. 文档间关联关系图

```
00-overview.md
  ├── 引用 → 01-phase1 (Phase 1说明)
  ├── 引用 → 02-phase2 (Phase 2说明)
  ├── 引用 → 02-phase2.5 (Phase 2.5说明) ★
  ├── 引用 → 03-phase3 (Phase 3说明)
  ├── 引用 → 04-phase4 (Phase 4说明)
  ├── 引用 → 05-phase5 (Phase 5说明)
  └── 引用 → 06-phase6 (Phase 6说明)

01-phase1-foundation.md
  └── 产出被所有后续Phase依赖:
      - model/* → Phase 2.5/3/4/5 使用
      - infrastructure/* → Phase 2.5/3/6 使用
      - browser/CefBrowserPanel → Phase 3 StreamingOutputEngine 使用
      - config/CCGuiConfig → Phase 3 ConfigManager 使用

02-phase2-adaptation.md (部分废弃)
  ├── 保留 → StreamCallback, SimpleStreamCallback → Phase 2.5 BridgeManager 回调
  ├── 保留 → ProcessPool → 可选，用于CLI进程预热
  ├── 保留 → VersionDetector → 启动时CLI版本检测
  ├── 保留 → MessageParser → 辅助，处理非SDK格式文本
  └── 废弃 → AnthropicProvider, OpenAIProvider, DeepSeekProvider, MultiProviderAdapter

02-phase2.5-claude-code-sdk.md ★ 核心关键路径
  ├── 依赖 ← Phase 1 (JsonUtils, EventBus, PluginException, logger)
  ├── 产出 → ClaudeCodeClient → Phase 3 ChatOrchestrator 使用
  ├── 产出 → StreamJsonParser → Phase 6 测试覆盖
  ├── 产出 → SdkSessionManager → Phase 3 SessionManager 协同
  ├── 产出 → ModelInfoRegistry → Phase 3 ModelSwitcher 使用
  └── 产出 → SdkConfigBuilder → Phase 5 McpServerManager 使用

03-phase3-core-services.md (需SDK适配)
  ├── ChatOrchestrator → 调用 ClaudeCodeClient (Phase 2.5)
  ├── StreamingOutputEngine → 桥接到 CefBrowserPanel (Phase 1)
  ├── ConfigManager → 使用 CCGuiConfig (Phase 1)
  ├── SessionManager → 使用 SessionStorage (Phase 1) + SdkSessionManager (Phase 2.5)
  └── ModelSwitcher → 使用 ModelInfoRegistry (Phase 2.5)

04-phase4-features.md (需SDK适配)
  ├── InteractiveRequestEngine → 接收 SdkAssistantMessage (Phase 2.5)
  ├── PromptOptimizer → 调用 ClaudeCodeClient (Phase 2.5)
  ├── MultimodalInputHandler → 独立，无SDK依赖
  └── 废弃 → T4.6 OpenAIProvider/DeepSeekProvider

05-phase5-ecosystem.md (需SDK适配)
  ├── McpServerManager → 生成 McpServersConfig 给 SdkConfigBuilder (Phase 2.5)
  ├── SkillExecutor → 调用 ChatOrchestrator (Phase 3)
  └── ScopeManager → 管理 SdkOptions 配置 (Phase 2.5)

06-phase6-optimization.md (需SDK适配)
  ├── 测试覆盖 → 新增 Phase 2.5 和 Phase 5 组件测试
  ├── 废弃 → HttpClientPool
  └── 其余优化组件 → LazyCefBrowserPanel, JcefMemoryManager, MarkdownCache 等（不变）
```

---

## 7. 风险与注意事项

### 高风险项

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `claude` CLI 未安装 | 插件不可用 | 启动时 `VersionDetector.isCliInstalled()` 检测 + 引导安装 |
| stream-json 协议变更 | 解析失败 | 版本检测 + 兜底为纯文本模式 `--output-format text` |
| CLI 子进程挂起 | 资源泄漏 | 超时机制（默认30s）+ `destroyForcibly()` |
| JCEF 不可用（无Chromium） | UI无法渲染 | 降级为 Swing 原生组件（预留接口） |
| Windows 路径问题 | MCP 配置文件路径错误 | 使用 `File.createTempFile()` 绝对路径 |

### 技术债务清单

| 项目 | 来源 | 建议处理时机 |
|------|------|-------------|
| `SecureStorage` 未接入 `PasswordSafe` | Phase 1 TODO | Phase 1 实现时直接修复 |
| `EventBus.publish()` 无订阅者时丢事件 | Phase 1 Bug | Phase 1 实现时直接修复 |
| `ConfigStorage` 缺失 `topP`/`checkpointEnabled` 持久化 | Phase 1 遗漏 | Phase 1 实现时直接修复 |
| `ChatSession` 的 `var` 字段导致 `data class` 行为不符预期 | Phase 1 设计问题 | Phase 1 实现时重构为不可变+状态管理 |
| `StreamJsonParser` 未覆盖 `content_block_delta` 流式增量事件 | Phase 2.5 待验证 | Phase 2.5 实现时根据实际CLI输出补充 |
| `SdkPermissionHandler` 与 `InteractiveRequestEngine` 的交互路径未统一 | Phase 2.5/4 设计交叉 | Phase 4 实现时统一为 `InteractiveRequestEngine` 接收 `SdkAssistantMessage` |

---

## 8. 术语表

| 术语 | 含义 |
|------|------|
| **SDK** | Claude Code SDK，即 `claude` CLI 工具，插件通过子进程方式集成 |
| **stream-json** | CLI 的 `--output-format stream-json` 输出格式，NDJSON（每行一个JSON） |
| **NDJSON** | Newline Delimited JSON，以换行符分隔的JSON对象流 |
| **JCEF** | Java Chromium Embedded Framework，IntelliJ 内嵌的 Chromium 浏览器 |
| **JBCefJSQuery** | JCEF 提供的 JS→Java 双向通信机制 |
| **MCP** | Model Context Protocol，Claude Code 的工具服务器协议 |
| **SSE** | Server-Sent Events，HTTP 流式协议（本插件不直接使用，SDK内部使用） |
| **EDT** | Event Dispatch Thread，Swing/UI 线程，禁止在此线程执行阻塞操作 |
| **StateFlow** | Kotlin 协程的响应式状态容器，线程安全 |
| **Disposable** | IntelliJ 生命周期接口，用于资源清理 |
| **PersistentStateComponent** | IntelliJ 配置持久化机制，存储到 `.idea/` 目录 |
| **PasswordSafe** | IntelliJ 加密存储 API，基于系统密钥链 |

---

## 9. 常见问题

### Q: 为什么不直接调用 Anthropic REST API？
A: 本插件定位是 Claude Code 的 GUI 前端，不是独立的 AI 客户端。Claude Code SDK 提供了完整的工具链（Bash、Read、Write、Grep 等）、MCP 支持、会话管理，这些能力无法通过直接调用 API 获取。

### Q: 为什么还需要 `model/provider/` 包里的类型？
A: `ChatRequest`、`ChatResponse`、`ChatChunk` 等类型在内部消息传递中仍有用途（如 `ChatOrchestrator` 到 `StreamingOutputEngine` 的桥接）。但 `AIProvider` 接口及其实现类不再需要。

### Q: Phase 2 的组件还需要实现吗？
A: **部分需要**。`StreamCallback`、`SimpleStreamCallback`、`ProcessPool`、`VersionDetector`、`MessageParser` 仍需实现。`AnthropicProvider`、`OpenAIProvider`、`DeepSeekProvider`、`MultiProviderAdapter` 不需要实现。`StdioBridge` 和 `BridgeManager` 由 Phase 2.5 重构。

### Q: 前端 React 代码在哪里？
A: 前端代码不在本文档范围内。React bundle 预编译后放入 `resources/` 目录，由 `CefBrowserPanel.loadHtmlPage()` 加载。

### Q: 如何调试 CLI 通信？
A: 在 `SdkConfigBuilder` 中添加 `--verbose` 标志（需确认CLI是否支持），或在 `ClaudeCodeClient` 中查看 stderr 输出日志。

---

**文档结束** — 祝开发顺利！
