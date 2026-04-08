# ClaudeCodeJet 后端开发指引与执行计划

**文档版本**: 2.0
**创建日期**: 2026-04-08
**最后更新**: 2026-04-08（基于代码审查的实际进度分析）

---

## 1. 当前开发进度总览

基于对全部 57 个 Kotlin 源文件的逐文件审查，当前后端整体完成度约 **52%**：

| Phase | 规划 | 已实现 | 完成度 | 核心状态 |
|-------|------|--------|--------|---------|
| Phase 1 基础设施+数据模型 | 38文件 | 33文件 | **70%** | 大部分完成，CCGuiConfig缺失（编译报错） |
| Phase 2 通信适配(保留) | 4文件 | 2文件 | **50%** | BridgeManager+StreamCallback完成 |
| Phase 2.5 SDK集成 | 6文件 | 5文件 | **95%** | 核心链路完整，缺SdkPermissionHandler |
| Phase 3 核心应用服务 | 10文件 | 4文件 | **40%** | ChatOrchestrator/Session/Streaming/Config完成 |
| Phase 4 功能模块 | 6文件 | 4文件 | **85%** | 含小TODO，缺引用系统和任务解析器 |
| Phase 5 生态集成 | 6文件 | 6文件 | **65%** | CRUD完成，3个execute方法是空壳 |
| Phase 6 优化+测试 | 32文件 | 0文件 | **0%** | 完全未开始 |

### 编译阻塞项

| 阻塞 | 位置 | 原因 |
|------|------|------|
| **CCGuiConfig 类不存在** | plugin.xml 第24行 | 引用了 `com.github.xingzhewa.ccgui.config.CCGuiConfig` 但该类未实现 |

---

## 2. 文档体系与阅读顺序

```
design/docs/backend/
├── 00-overview.md              ← 总览：架构、阶段、规范
├── 01-phase1-foundation.md     ← Phase 1: 38文件的数据模型与基础设施设计
├── 02-phase2-adaptation.md     ← Phase 2: 通信适配层（部分废弃，顶部有修订说明）
├── 02-phase2.5-claude-code-sdk.md ← ★ Phase 2.5: SDK集成（核心关键路径）
├── 03-phase3-core-services.md  ← Phase 3: 核心服务（顶部有SDK适配修订）
├── 04-phase4-features.md       ← Phase 4: 功能模块（顶部有SDK适配修订）
├── 05-phase5-ecosystem.md      ← Phase 5: 生态集成（顶部有SDK适配修订）
├── 06-phase6-optimization.md   ← Phase 6: 优化与测试（顶部有SDK适配修订）
└── 07-development-guide.md     ← 本文档
```

**推荐阅读顺序**：
1. `00-overview.md` §2 架构图 + §4 依赖关系
2. `02-phase2.5-claude-code-sdk.md` §1 数据流 — 理解SDK集成为何是核心
3. 本文档 §3 核心概念 + §4 执行计划
4. `01-phase1-foundation.md` — 掌握所有数据模型定义
5. `03-phase3-core-services.md` 顶部修订 + ChatOrchestrator — 理解消息处理链路

---

## 3. 核心概念速查

### 3.1 插件定位

```
Claude Code CLI (命令行工具) ←→ ClaudeCodeJet (JetBrains IDE GUI前端)
```

所有AI能力通过 `ClaudeCodeClient` → `claude` CLI子进程获取，不直接调用任何AI供应商REST API。

### 3.2 核心数据流

```
用户输入 → ChatOrchestrator → ClaudeCodeClient → ProcessBuilder("claude -p ...")
  → CLI子进程 → stdout NDJSON → StreamJsonParser → SdkMessage分发
    → StreamingOutputEngine → CefBrowserPanel.sendToJavaScript() → React前端
```

### 3.3 已实现的关键类

| 类 | 包路径 | 状态 |
|----|--------|------|
| ClaudeCodeClient | adaptation.sdk | 完成 |
| StreamJsonParser | adaptation.sdk | 完成 |
| SdkConfigBuilder | adaptation.sdk | 完成 |
| SdkSessionManager | adaptation.sdk | 完成 |
| SdkMessageTypes | adaptation.sdk | 完成 |
| ChatOrchestrator | application.orchestrator | 完成 |
| SessionManager | application.session | 完成 |
| StreamingOutputEngine | application.streaming | 完成 |
| ConfigManager | application.config | 完成 |
| BridgeManager | bridge | 完成 |
| CefBrowserPanel | browser | 7个handler是TODO |

### 3.4 SDK CLI 参数速查

| 参数 | 作用 | SdkOptions字段 |
|------|------|---------------|
| `-p <prompt>` | 发送提示 | sendMessage的prompt参数 |
| `--output-format stream-json` | 输出格式 | 常量 |
| `--resume <sessionId>` | 恢复会话 | resumeSessionId |
| `--continue` | 继续最近对话 | continueRecent |
| `--model <modelId>` | 指定模型 | model |
| `--system-prompt <text>` | 系统提示 | systemPrompt |
| `--mcp-config <file>` | MCP配置 | mcpConfig |
| `--allowedTools <list>` | 限制工具 | allowedTools |
| `--max-turns <n>` | 最大轮次 | maxTurns |

---

## 4. 后端开发执行计划

基于审查结果，将后续开发分为4个执行阶段，按阻塞优先级排序。

### Stage A：消除编译阻塞（1天）

| 编号 | 任务 | 文件 | 优先级 |
|------|------|------|--------|
| A1 | 创建CCGuiConfig.kt | `config/CCGuiConfig.kt` | P0 |
| A2 | 修复SessionContext重复ModelConfig | `model/session/SessionContext.kt` | P0 |

### Stage B：完善核心功能链路（3天）

| 编号 | 任务 | 文件 | 优先级 |
|------|------|------|--------|
| B1 | 实现CefBrowserPanel的7个TODO handler | `browser/CefBrowserPanel.kt` | P0 |
| B2 | 创建SdkPermissionHandler | `adaptation/sdk/SdkPermissionHandler.kt` | P1 |
| B3 | 接线SkillExecutor到ChatOrchestrator | `application/skill/SkillExecutor.kt` | P1 |
| B4 | 接线AgentExecutor到ChatOrchestrator | `application/agent/AgentExecutor.kt` | P1 |
| B5 | 实现3个storage loader持久化 | SkillsManager/AgentsManager/McpServerManager | P1 |
| B6 | 替换模板启动逻辑 | `startup/` + `services/` | P2 |

### Stage C：补齐缺失组件（3天）

| 编号 | 任务 | 文件 | 优先级 |
|------|------|------|--------|
| C1 | 创建VersionDetector | `adaptation/version/VersionDetector.kt` | P1 |
| C2 | 创建ContextProvider | `application/context/ContextProvider.kt` | P1 |
| C3 | 创建ModelSwitcher | `application/config/ModelSwitcher.kt` | P2 |
| C4 | 创建ConfigHotReloadManager | `application/config/ConfigHotReloadManager.kt` | P2 |
| C5 | 创建ConversationReferenceSystem | `application/reference/` | P2 |
| C6 | 更新plugin.xml注册所有服务 | `plugin.xml` | P1 |

### Stage D：测试与质量保障（5天）

| 编号 | 任务 | 优先级 |
|------|------|--------|
| D1 | StreamJsonParser单元测试 | P0 |
| D2 | SdkConfigBuilder单元测试 | P0 |
| D3 | ChatOrchestrator集成测试 | P1 |
| D4 | SessionManager测试 | P1 |
| D5 | CefBrowserPanel handler测试 | P1 |
| D6 | 数据模型序列化测试 | P2 |

---

## 5. 开发前必读清单

- [ ] 理解SDK集成模型：`ClaudeCodeClient` 通过 `ProcessBuilder` 调用 `claude` CLI
- [ ] 理解JCEF双向通信：`CefBrowserPanel` (Java→JS via `sendToJavaScript`，JS→Java via `JBCefJSQuery`)
- [ ] 确认环境：`claude` CLI 已安装且 `claude --version` 可执行
- [ ] 确认废弃组件：不实现 AnthropicProvider、OpenAIProvider、DeepSeekProvider

### 关键约束

1. 禁止直接调用AI供应商REST API — 一切通过ClaudeCodeClient
2. 禁止阻塞EDT — 异步操作用 `CoroutineScope(Dispatchers.IO)`
3. 禁止使用GlobalScope — 用项目级scope或 `createDisposableScope`
4. 服务必须注册 — `@Service` 或 plugin.xml
5. 资源必须释放 — 实现 `Disposable`

---

## 6. 代码审查中发现的设计偏差

以下偏差均为**改进**（实现优于设计），不需要回退：

| 偏差 | 设计 | 实现 | 判定 |
|------|------|------|------|
| ChatSession字段 | `var updatedAt/isActive` | 全部`val`，用`copy()` | 改进：避免并发问题 |
| SessionStorage | 文件IO `.idea/ccgui-sessions/` | PersistentStateComponent | 改进：更IDE原生 |
| EventBus订阅绑定 | Disposable绑定 | WeakReference+自动清理 | 改进：防内存泄漏 |
| ErrorRecoveryManager | 简单retry | 策略模式5种策略 | 改进：更灵活 |
| ConfigStorage | 扁平State字段 | JSON字符串整体序列化 | 改进：减少字段遗漏 |

### 需要修复的实现问题

| 问题 | 文件 | 处理时机 |
|------|------|---------|
| SessionContext内嵌重复ModelConfig | SessionContext.kt | Stage A |
| ConversationMode枚举值与设计不同 | ConversationMode.kt | 保持现状（实现值更贴合SDK） |
| PromptOptimizer.normalizeCodeBlocks()空操作 | PromptOptimizer.kt | Stage C |
| MultimodalInputHandler.getImageDimensions()返回null | MultimodalInputHandler.kt | Stage C |

---

## 7. 术语表

| 术语 | 含义 |
|------|------|
| SDK | Claude Code SDK，即 `claude` CLI 工具 |
| stream-json | CLI的 `--output-format stream-json` 输出格式，NDJSON |
| JCEF | Java Chromium Embedded Framework，IntelliJ内嵌浏览器 |
| JBCefJSQuery | JCEF提供的JS→Java双向通信机制 |
| MCP | Model Context Protocol，Claude Code的工具服务器协议 |
| EDT | Event Dispatch Thread，Swing UI线程 |

---

**文档结束**
