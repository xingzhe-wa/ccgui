# Sprint 3 断点 Prompt：端到端联调

> **角色**：你是一个同时精通 Kotlin（IntelliJ Platform SDK）和 TypeScript（React）的全栈开发者，擅长调试跨语言通信。
> **目标**：在 IDEA 中运行插件，打通 "用户发消息 → Claude AI 流式回复 → 界面渲染" 的完整链路。
> **前置条件**：Sprint 1（构建通过）和 Sprint 2（主题生效）均已完成。

---

## 项目背景

**ClaudeCodeJet** 是一个 IntelliJ IDEA 插件，架构如下：

```
[用户输入] → React前端 (JCEF)
    ↓ JBCefJSQuery (JS→Java)
[CefBrowserPanel.kt] → [BridgeManager.kt] → [ChatOrchestrator.kt]
    ↓
[ClaudeCodeClient.kt] → 启动 claude CLI 子进程
    ↓ 流式JSON输出
[StreamJsonParser] → [StreamingOutputEngine]
    ↓ executeJavaScript (Java→JS)
[window.ccEvents.emit] → [useStreaming hook] → [StreamingMessage组件]
```

**当前状态**：前后端代码各自写完，但**从未在 IDEA 实例中联调过**。一定会有运行时 bug。

## 你的任务

### Task 3.1：runIde 基础验证

1. 执行 `./gradlew runIde`
2. 在启动的 IDEA 实例中找到 "CCGUI" Tool Window（右侧栏）
3. 点击打开，确认：
   - 前端页面加载（不白屏、不报错）
   - 如果白屏，检查 IDEA 的 `idea.log` 日志文件

**如果白屏，排查顺序**：
1. `MyToolWindowFactory.kt` 的 `loadFrontendPage()` 是否成功加载了 HTML
2. `CefBrowserPanel.kt` 的 `init()` 是否成功创建了 JCEF 浏览器实例
3. 前端资源是否在 classpath 中（Sprint 1 的打包是否生效）
4. JCEF 是否可用（某些 IDE 版本/OS 组合可能不支持）

### Task 3.2：Bridge 通信链路验证

**目标**：确认 `window.ccBackend` 和 `window.ccEvents` 在 JS 侧可用。

1. 在 `CefBrowserPanel.kt` 的 `init()` 方法中，找到 JS bridge 注入逻辑
2. 关键检查点：
   - `JBCefJSQuery` 是否正确创建并注册
   - `window.ccBackend.send()` 是否映射到了 Java handler
   - `window.ccEvents.emit()` 是否映射到了 Java → JS 的推送

**如果 `window.ccBackend` 不存在**：
- 检查 `CefBrowserPanel.kt` 中的 JS 注入代码是否执行
- 可能需要在 HTML 加载完成后（`CefBrowser.loadURL` 回调后）才注入
- 临时方案：直接在 `loadHtmlContent` 时内联 `<script>` 标签注入 bridge 对象

**调试手段**：
- 在 `CefBrowserPanel.kt` 的 action handler 中加 `println("=== ACTION: $action ===")`
- 在前端 `java-bridge.ts` 的 `invoke` 方法中加 `console.log`
- 打开 JCEF 的 DevTools：在 IDEA 中 `Help → Find Action → "Open CEF DevTools"`

### Task 3.3：消息发送链路

**链路**：
```
前端 ChatInput.onSend()
  → javaBridge.sendMessage(JSON)
  → JBCefJSQuery → CefBrowserPanel.handleAction("sendMessage")
  → BridgeManager.sendMessage()
  → ChatOrchestrator.sendMessage()
  → ClaudeCodeClient.sendMessage() → ProcessBuilder("claude", ...)
```

**逐步验证**：

1. **前端触发**：在 ChatInput 输入文字，点发送，打开 CEF DevTools Console 看：
   - `javaBridge.invoke('sendMessage', ...)` 是否被调用
   - 是否有超时错误（30s timeout）

2. **Java 接收**：看 IDEA 的 `idea.log` 或 console 输出：
   - `CefBrowserPanel` 的 `handleAction("sendMessage")` 是否触发
   - `BridgeManager.sendMessage()` 是否被调用

3. **CLI 启动**：
   - `ClaudeCodeClient.sendMessage()` 是否成功启动 `claude` CLI 进程
   - 检查 `SdkConfigBuilder.buildCommand()` 返回的命令列表
   - 在 `ClaudeCodeClient` 中加日志打印完整命令
   - 手动在终端执行该命令确认 CLI 可用：`claude --version`

4. **如果 CLI 不可用**：
   - 确认 `claude` 命令在 PATH 中
   - Windows 上可能需要 `claude.exe` 或完整路径
   - `MyProjectActivity.kt` 启动时检测了 CLI 版本，看日志

### Task 3.4：流式显示链路

**链路**：
```
CLI stdout (JSON lines)
  → StreamJsonParser.parseLine()
  → SdkEventListener.onTextDelta()
  → ChatOrchestrator 内部回调
  → StreamingOutputEngine.appendChunk()
  → CefBrowserPanel.sendToJavaScript("streaming_chunk", ...)
  → window.ccEvents.emit("streaming_chunk", ...)
  → useStreaming hook 的 EventBus 订阅
  → StreamingMessage 组件渲染
```

**逐步验证**：

1. CLI 输出是否被读取：
   - `ClaudeCodeClient` 的 `BufferedReader` 是否读到行
   - `StreamJsonParser.parseLine()` 是否返回有效 `SdkMessage`

2. Java → JS 推送是否生效：
   - `StreamingOutputEngine.appendChunk()` 是否调用了 `cefPanel?.sendToJavaScript()`
   - CEF DevTools Console 中是否看到 `streaming_chunk` 事件

3. 前端是否接收：
   - `useStreaming` hook 的 `EventBus.subscribe('streaming_chunk', ...)` 是否触发
   - `streamingStore.appendChunk()` 是否更新了 buffer

**如果流式不通**：
- 临时降级方案：改为非流式，等 CLI 完成后一次性返回完整消息
- 检查 `StreamingOutputEngine.setCefPanel()` 是否被调用（panel 引用是否注入）

### Task 3.5：会话管理链路

1. 点击 "新建会话" → 前端调用 `window.ccBackend.createSession()`
2. Java 侧 `SessionManager.createSession()` → 创建并持久化
3. 前端 `appStore.createSession()` → 添加到 sessions 列表
4. 切换会话 → 发消息 → 切回 → 旧消息还在

## 需要读取的文件（按优先级排序）

### 后端（Kotlin）
| 文件 | 关注点 |
|------|--------|
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | JBCefJSQuery 注入、action handler 分发、sendToJavaScript |
| `src/main/kotlin/.../bridge/BridgeManager.kt` | sendMessage/streamMessage 转发逻辑 |
| `src/main/kotlin/.../orchestrator/ChatOrchestrator.kt` | 编排逻辑、流式回调连接 |
| `src/main/kotlin/.../sdk/ClaudeCodeClient.kt` | CLI 进程启动、流式读取 |
| `src/main/kotlin/.../sdk/StreamJsonParser.kt` | JSON 行解析 |
| `src/main/kotlin/.../sdk/SdkConfigBuilder.kt` | CLI 命令构建 |
| `src/main/kotlin/.../streaming/StreamingOutputEngine.kt` | 流式输出推送到 JCEF |
| `src/main/kotlin/.../session/SessionManager.kt` | 会话 CRUD |
| `src/main/kotlin/.../toolWindow/MyToolWindowFactory.kt` | 前端加载逻辑 |

### 前端（TypeScript）
| 文件 | 关注点 |
|------|--------|
| `webview/src/lib/java-bridge.ts` | JBCefJSQuery 封装、invoke 调用 |
| `webview/src/shared/hooks/useJavaBridge.ts` | React 层 bridge 封装 |
| `webview/src/shared/hooks/useStreaming.ts` | 流式事件订阅 |
| `webview/src/shared/stores/streamingStore.ts` | 流式状态 |
| `webview/src/shared/stores/appStore.ts` | 全局状态、后端调用 |
| `webview/src/shared/stores/sessionStore.ts` | 会话状态 |
| `webview/src/shared/utils/event-bus.ts` | 前端事件总线 |
| `webview/src/main/pages/ChatView.tsx` | 聊天页面组装 |
| `webview/src/main/components/JcefBrowser.tsx` | JCEF 环境就绪检测 |

## 调试技巧

1. **打开 CEF DevTools**：IDEA → `Help` → `Find Action` → 搜索 "Open CEF DevTools"
2. **后端日志**：`idea.log` 位于 `{user.home}/AppData/Local/JetBrains/IntelliJIdea2025.2/log/idea.log`
3. **CLI 调试**：手动在终端运行 `claude -p "hello" --output-format stream-json` 确认 CLI 本身工作正常
4. **线程问题**：JCEF 回调在非 EDT 线程，如果更新 UI 必须用 `ApplicationManager.getApplication().invokeLater()`
5. **JSON 格式**：在 `StreamJsonParser` 和 `CefBrowserPanel` 中打印原始 JSON 确认格式匹配

## 验收标准

- [ ] `./gradlew runIde` 启动成功，CCGUI Tool Window 打开不白屏
- [ ] 发送 "Hello" 消息后，收到 Claude AI 的回复
- [ ] 回复内容流式逐字显示（非一次性出现）
- [ ] 创建新会话成功
- [ ] 切换会话后，各会话消息独立不混淆
- [ ] 停止按钮可中断流式输出
- [ ] 无 Claude CLI 时有友好提示（不崩溃）

## 交接给下一位开发者

> "Sprint 3 完成。核心聊天链路已跑通：发消息→流式回复→界面渲染。会话管理可用。以下是联调中修复的具体问题：[列出问题]。请进入 Sprint 4：修 Bug + 稳定性。"

**必须记录**：
1. 联调中发现的所有 bug 及修复方案
2. Bridge 通信的最终调试参数（如超时时间、缓冲区大小等）
3. Windows 特有的问题（路径、进程管理）
4. CLI 命令的最终格式
