# CC Assistant 开发技能手册

## 使用说明

本目录包含 CC Assistant (CCGUI) IntelliJ 插件的可复用开发技能文档。每篇文档针对一个特定技术领域，记录已知的坑点、正确模式、诊断流程和检查清单。

目标读者：参与本项目开发的 Kotlin 后端工程师和 React/TypeScript 前端工程师。

## 技能列表

| 技能编号 | 技能名称 | 适用场景 | 关键文件 |
|---------|---------|---------|---------|
| 01 | [JCEF 页面生命周期管理](./01-jcef-page-lifecycle.md) | JCEF 浏览器初始化、页面加载、Bridge 注入时机排查 | `CefBrowserPanel.kt`, `MyToolWindowFactory.kt` |
| 02 | [Java <-> JavaScript 通信桥接](./02-java-js-bridge.md) | 前后端消息收发、参数格式、JSON 转义问题排查 | `CefBrowserPanel.kt`, `java-bridge.ts`, `ChatView.tsx` |
| 03 | 流式输出与事件系统 | 流式消息推送、chunk/complete/error 事件处理、Zustand store 状态管理 | `CefBrowserPanel.kt`, `streamingStore.ts`, `useStreaming.ts`, `event-bus.ts` |
| 04 | 会话与配置管理 | 会话 CRUD、配置持久化、主题切换、ModelConfig 更新 | `SessionManager.kt`, `ConfigManager.kt`, `sessionStore.ts` |
| 05 | Skill 与 Agent 执行 | 自定义 Skill/Agent 的创建、执行、异步结果回调 | `SkillExecutor.kt`, `AgentsManager.kt`, `skillsStore.ts`, `agentsStore.ts` |
| 06 | MCP Server 管理 | MCP Server 生命周期管理、启动/停止/测试、Scope 权限 | `McpServerManager.kt`, `mcpStore.ts`, `McpServerManager.tsx` |
| 07 | 交互式问题与多模态输入 | InteractiveQuestion 弹窗、图片/文件附件、Prompt 优化 | `InteractiveRequestEngine.kt`, `MultimodalInputHandler.kt`, `questionStore.ts`, `useStreaming.ts` |
| 08 | [PRD-SDK 合规开发指南](./08-prd-sdk-compliance.md) | PRD-v3.1 规格遵循、SDK 集成规范、bug 修复时新盲点记录 | `PRD-v3.1.md`, `claude-sdk-integration-guide.md`, `00-development-pitfalls-and-lessons.md` |

## 如何使用

**新功能开发前** -- 阅读相关 skill 文档，避免重复踩坑。例如：
- 要添加新的 Action Handler，先读 skill 02 了解参数解析的正确方式
- 要修改前端页面，先读 skill 01 了解 Bridge 注入时序

**Bug 修复时** -- 按 skill 中的诊断流程逐步排查：
- 页面白屏 / Bridge 未就绪 -> skill 01 检查清单
- 消息发送无响应 / 参数丢失 -> skill 02 的参数嵌套章节
- 流式输出中断 / chunk 丢失 -> skill 03 的事件流程图

**Code Review 时** -- 对照 skill 中的检查清单逐项审查：
- loadURL 后是否有 onLoadEnd 等待机制？（skill 01）
- sendToJavaScript 的 JSON 是否正确转义了单引号和反斜杠？（skill 02）
- 异步 Action 是否正确通过 `scope.launch` + 事件回调处理？（skill 02）
