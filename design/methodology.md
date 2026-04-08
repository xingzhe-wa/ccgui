# ClaudeCodeJet 插件开发方法论手册

> **适用版本**：v0.0.1 及后续版本
> **维护者**：插件开发团队
> **最后更新**：2026-04-09
> **背景**：基于 Sprint 1（构建流水线）→ Sprint 2（CSS 主题系统）→ Sprint 3（端到端联调）→ Sprint 4（修 Bug + 基础稳定）→ Sprint 5（Bridge 补全 + 权限流）的完整开发周期经验沉淀。

---

## 一、方法论总览

### 1.1 三阶段渐进验证模型

插件开发的复杂度来自"三重运行环境"的叠加：

```
[开发环境]          [IDE 环境]              [用户环境]
 Vite Dev Server  →  IntelliJ IDEA (JCEF)  →  用户安装插件
 前端独立预览        插件运行 + JCEF          生产环境
```

**核心方法论**：每一阶段验证通过后再进入下一阶段，而非等到最后才做端到端验证。

| 阶段 | 验证目标 | 关键指标 | 可独立运行 |
|------|---------|---------|-----------|
| Sprint 1 | 构建流水线打通 | `./gradlew buildPlugin` 成功，JAR 含 webview assets | ✅ |
| Sprint 2 | 主题切换生效 | `globals.css` HSL 变量链路完整 | ✅（浏览器） |
| Sprint 3 | 端到端联调 | 消息发送→流式渲染完整链路 | ❌（需 IDE） |

### 1.2 问题拆解思路："Gap-First" 分析法

遇到跨系统问题时，首先识别"Gap"（断裂点）在哪：

```
[前端组件] → [前端 Store/状态] → [Bridge (JS→Java)] → [Kotlin Handler] → [后端服务] → [CLI/外部]
              ↑ gap                ↑ gap              ↑ gap
```

每个 gap 都有可能断裂。排查时从左到右逐层验证，而非猜测。

**本次 Sprint 3 的三个 Gap**：
- Gap 1：`injectBackendJavaScript()` 从未调用 → `window.ccBackend` 不存在
- Gap 2：Java 事件名与 JS EventBus 常量名不匹配 → 事件石沉大海
- Gap 3：`StreamingOutputEngine.setCefPanel()` 未调用 → 流式数据无推送通道

### 1.3 跨语言调试策略

#### 分层验证原则

调试 JCEF 跨语言问题时，按以下顺序分层排查：

**Layer 0 — 环境就绪**：验证 JCEF 进程本身是否启动
```
日志中搜索 "CefServer instance. Transport port=xxxx"
说明 JCEF 渲染进程已成功初始化
```

**Layer 1 — Bridge 就绪**：验证 JS→Java 通道
```
前端 console: window.ccBackend && window.ccEvents ? "OK" : "MISSING"
若 MISSING → 检查 injectBackendJavaScript() 是否被调用
```

**Layer 2 — Handler 触发**：验证 Java Handler 收到请求
```
在 handleJsRequest() 第一行加 println("=== ACTION: $action ===")
发送消息后看 IDEA console 输出
```

**Layer 3 — 响应回路**：验证 Java→JS 通道
```
在 sendToJavaScript() 第一行加 println(">>> JS event: $event")
事件应有输出
```

**Layer 4 — 前端订阅**：验证 React EventBus 收到事件
```
在 eventBus.emit() 前加 console.log
或使用 CEF DevTools: Help → Find Action → "Open CEF DevTools"
```

#### 最小化复现环境

遇到复杂问题时，先在最小化环境验证：
- **前端独立**：`npm run dev` + mock bridge → 验证 UI 组件逻辑
- **后端独立**：单元测试或 `claude -p "hello" --output-format stream-json` 验证 CLI
- **Bridge 独立**：在 `main/index.tsx` 中直接调用 `window.ccBackend.send()` 并观察

永远不要在完整 IDE 环境中调试未经验证的假设。

---

## 二、关键经验沉淀

### 2.1 JBCefJSQuery 通信机制详解

#### 工作原理

JBCefJSQuery 是 IntelliJ Platform 提供的 JS→Java 双向通信机制，内部实现如下：

```
[主页面 JS] --postMessage--> [隐藏 iframe (data URL)] --location.href=javascript:--> [Kotlin Handler]
```

**关键细节**：
1. JBCefJSQuery 在初始化时创建一个**隐藏 iframe**，其 `src` 为 `javascript:` URL
2. 该 iframe 中注入了 `__jcef_query_<randomId>__` 函数
3. 调用该函数时，内部通过 `location.href = 'javascript:...handler(request)...'` 触发 Kotlin
4. Kotlin 处理后通过 `executeJavaScript` 将结果返回主页面

#### 本插件的自定义 Bridge 架构

由于原始 JBCefJSQuery 的函数名是随机的（无法预测 `__jcef_query_<id>__` 中的 `<id>`），本插件采用**自定义 iframe Bridge**：

```
1. 创建隐藏 iframe，加载 data URL，内嵌 JS 脚本
2. 该脚本监听 postMessage，定义 window.javaRequestCallback
3. window.ccBackend.send() → iframe.contentWindow.postMessage()
4. iframe 收到消息后，调用 window.javaRequestCallback
5. window.javaRequestCallback 通过查找 JBCefJSQuery iframe 的 __jcef_query_<id>__ 函数触发 Kotlin
6. Kotlin handleJsRequest() 处理后，调用 sendToJavaScript("response", data)
7. sendToJavaScript() 通过 executeJavaScript 调用 window.ccEvents.emit("response", data)
8. 前端 ccEvents 拦截 "response" 事件，resolve javaBridge.invoke() 的 Promise
```

**架构图**：
```
[React App]
    ↓
[javaBridge.invoke('sendMessage', {queryId, action, params})]
    ↓
[window.ccBackend.send(request)]
    ↓
[隐藏 iframe] → [window.javaRequestCallback]
    ↓
[JBCefJSQuery iframe: __jcef_query_<id>__(JSON.stringify(request))]
    ↓
[Kotlin: handleJsRequest() 处理请求]
    ↓
[BridgeManager → ClaudeCodeClient → CLI 子进程]
    ↓
[Kotlin: onTextDelta() 回调]
    ↓
[StreamingOutputEngine.appendChunk(chunk)]
    ↓
[CefBrowserPanel.sendToJavaScript("streaming:chunk", {messageId, chunk})]
    ↓
[getCefBrowser().executeJavaScript("window.ccEvents.emit(...)")]
    ↓
[JS: window.ccEvents.emit("streaming:chunk", {messageId, chunk})]
    ↓
[JS: responseInterceptor 拦截 "response" 事件 → resolve Promise]
    ↓
[JS: eventBus.emit(Events.STREAMING_CHUNK, {messageId, chunk})]
    ↓
[React: useStreaming hook → StreamingMessage 组件渲染]
```

### 2.2 CSS 变量体系断裂问题

#### 根因

TailwindCSS 使用 HSL 格式的 CSS 变量：
```css
--primary: 217 33% 17%;  /* 空格分隔的 HSL 值 */
bg-primary → background-color: hsl(var(--primary))
```

但 `applyTheme()` 错误地设置了 HEX 变量名：
```ts
root.style.setProperty('--color-primary', '#0d47a1'); // 变量名不同，且为 HEX 格式
```

结果：Tailwind 的 `bg-primary` 读取 `var(--primary)`（固定值），自定义的 `--color-primary` 无人使用。

#### 解决方案

统一为**双层 CSS 变量架构**：

```
[ThemeConfig (HEX)] → [hexToHsl() 转换] → [CSS Variables (HSL)] → [Tailwind 类]
```

```ts
// themeStore.ts
function hexToHsl(hex: string): string {
  // "#0d47a1" → "217 81% 34%"（空格分隔）
  return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
}

function applyTheme(theme: ThemeConfig): void {
  root.style.setProperty('--primary', hexToHsl(colors.primary));    // HSL 格式
  root.style.setProperty('--userMessage', hexToHsl(colors.userMessage));
  // ...
}
```

**三层变量定义**：
1. `globals.css :root` — 亮色默认值（静态）
2. `globals.css .dark` — 暗色覆盖（静态）
3. `themeStore.applyTheme()` — 运行时动态覆盖（动态）

### 2.3 跨语言事件命名规范

**教训**：事件名是跨语言契约，必须在设计阶段确定，不得随意修改。

**强制规范**：
```
Java 发送事件名  =  JS EventBus 常量值
```

本插件确定的事件名映射：

| Java 调用 | 发送的事件名 | JS EventBus 常量 | JS 期望的 data 格式 |
|-----------|------------|-----------------|-------------------|
| `sendToJavaScript("streaming:chunk", {messageId, chunk})` | `streaming:chunk` | `Events.STREAMING_CHUNK` | `{messageId: string, chunk: string}` |
| `sendToJavaScript("streaming:complete", {messageId})` | `streaming:complete` | `Events.STREAMING_COMPLETE` | `{messageId: string}` |
| `sendToJavaScript("streaming:error", {messageId, error})` | `streaming:error` | `Events.STREAMING_ERROR` | `{messageId: string, error: string}` |
| `sendToJavaScript("streaming:cancel", {messageId})` | `streaming:cancel` | `Events.STREAMING_CANCEL` | `{messageId: string}` |
| `sendToJavaScript("response", {queryId, result})` | `response` | `javaBridge.init()` 监听 | `{queryId: number, result: any, error?: string}` |

### 2.5 Sprint 4 问题与修复

本节记录 Sprint 4（修 Bug + 基础稳定）中发现的问题及修复方案。

#### 问题 A：LoggerUtils.logger\<T\>() vs logger\<T\>() 混用导致运行时崩溃

**根因**：`ErrorRecoveryManager` 使用了 `LoggerUtils.logger<ErrorRecoveryManager>()`，但 `LoggerUtils.logger<T>()` 是内联再归函数，期望的是 `.logger<ErrorRecoveryManager>()` 扩展函数调用形式。两者虽名字相近但是不同的函数。

**修复**：统一使用扩展函数形式 `logger<ErrorRecoveryManager>()`。

**教训**：工具类的 static 方法和扩展函数不要混用同名。统一使用 `logger<T>()` 扩展函数风格。

#### 问题 B：React 应用缺少 ErrorBoundary 导致渲染错误时整页空白

**根因**：`App.tsx` 直接渲染 `JcefBrowser` → `AppRouter`，没有任何错误边界。React 组件树中任何一个组件抛出未捕获异常，都会导致整页白屏。

**修复**：添加 `ErrorBoundary` 类组件，捕获渲染异常，显示友好错误提示 + 重试按钮。

```tsx
class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }
  render(): ReactNode {
    if (this.state.hasError) {
      return <div>应用渲染出错...</div>;
    }
    return this.props.children;
  }
}
```

**教训**：所有 React 根组件必须包裹 ErrorBoundary，尤其是 JCEF 环境（无法使用浏览器 DevTools 调试控制台时）。

#### 问题 C：Store.initializeSessions() 从未在启动时调用

**根因**：`appStore.initializeSessions()` 在 `appStore.ts` 中定义，但没有任何地方在启动时调用它。导致插件启动后会话列表为空。

**修复**：在 `JcefBrowser` 的 `onReady` 回调中调用 `useAppStore.getState().initializeSessions()`。

**教训**：JCEF 环境中的前端 Store 初始化必须显式触发，不能依赖隐式的"应用启动"。`JcefBrowser` 是整个 React App 的根容器，`onReady` 是初始化前端状态的正确位置。

#### 问题 D：iframe 注入时 document.body 可能尚不存在

**根因**：`injectBackendJavaScript()` 在 `loadHtmlPage()`/`loadHtmlContent()` 后通过 `EventQueue.invokeLater` 延迟执行，但 iframe 注入脚本使用：
```js
document.body ? document.body.appendChild(iframe)
  : document.addEventListener('DOMContentLoaded', function() { document.body.appendChild(iframe); })
```
这段逻辑看起来"健壮"，但 `document.body` 为 null 时注册 `DOMContentLoaded` 事件，如果此时页面已处于 `complete` 状态，事件不会触发，iframe 永远不会注入，`window.ccBackend` 也不存在。

**修复**：改用 `document.addEventListener('DOMContentLoaded', ...)` 统一处理，不依赖 `document.body` 的即时状态。

**教训**：在 JCEF 注入脚本中，永远不要假设 DOM 已就绪。统一使用事件监听模式。

#### 问题 E：JCEF 回调中更新 Swing UI 未使用 invokeLater

**根因**：JCEF 的 JS 回调运行在非 EDT 线程。如果在回调中直接更新 Tool Window 标题等 Swing 组件，会导致线程安全警告或崩溃。

**正确模式**：
```kotlin
ApplicationManager.getApplication().invokeLater {
    toolWindow.setTitle("新标题")
}
```

**教训**：所有 JCEF → Kotlin → Swing 的跨线程 UI 更新都必须包裹在 `invokeLater` 中。

---

### 2.6 流式输出线程安全模式

**背景**：StreamingOutputEngine 在 JCEF 回调（`onTextDelta`）中被调用，该回调运行在不确定的线程上。需要保证：
1. `_isStreaming` 和 `_buffer` 的并发读写安全
2. `cefPanel?.sendToJavaScript()` 的调用是线程安全的

**解决方案**：使用 `MutableStateFlow`，其在协程上下文外也能安全地并发写入。`sendToJavaScript` 通过 `invokeLater` 派发到 EDT。

**关键代码**：
```kotlin
// StreamingOutputEngine.appendChunk()
private val _isStreaming = MutableStateFlow(false)
private val _buffer = MutableStateFlow("")

fun appendChunk(chunk: String, messageId: String = "") {
    if (!_isStreaming.value) { _isStreaming.value = true }
    _buffer.value += chunk  // 线程安全
    EventBus.publish(SdkTextDeltaEvent(messageId, chunk))
    cefPanel?.sendToJavaScript("streaming:chunk", mapOf("messageId" to messageId, "chunk" to chunk))
}
```

**教训**：IDE 插件中混用 JCEF 线程、协程线程、Swing EDT 三种线程模型，所有跨线程通信都要显式处理。

---

### 2.7 Sprint 4.2/4.3 问题与修复

#### 问题 F：CLI 不可用时前端收到无意义错误

**根因**：`BridgeManager.sendMessage()` 和 `streamMessage()` 从不检查 CLI 是否可用，直接调用 `claudeClient.sendMessage()`。当 CLI 不可用时，子进程启动失败，产生包含 "cannot find program" 等系统级错误消息，对用户无意义。

**修复**：在 `BridgeManager` 的 `sendMessage()` 和 `streamMessage()` 方法中，异步 scope 启动前同步调用 `claudeClient.isCliAvailable()` 检查：
```kotlin
if (!claudeClient.isCliAvailable()) {
    val errorMsg = "Claude CLI is not installed or not available. Please install it from https://..."
    callback.onStreamError(errorMsg)
    onResponse?.invoke(null, errorMsg)
    return  // 立即返回，不启动异步 scope
}
```

**教训**：外部依赖（CLI）的可用性必须在使用前检查，不能依赖运行时异常来发现。"快速失败"原则——在进入异步工作流之前同步验证。

#### 问题 G：已注册但从未使用的服务造成认知负担

**根因**：`MyProjectService` 在早期开发阶段创建，后来启动逻辑迁移到 `MyProjectActivity`，但文件未删除，且 `plugin.xml` 中未注册（成了"幽灵文件"）。

**修复**：删除 `MyProjectService.kt` 文件。确认服务在 `plugin.xml` 中无注册记录后再删除文件。

**教训**：定期执行 `plugin.xml` 注册表审计——对照 `grep "serviceImplementation" plugin.xml` 与实际使用的服务，确保一致。

#### 问题 H：同一 CLI 检查逻辑在两处重复实现

**根因**：`ClaudeCodeClient.isCliAvailable()` 实现了正确的超时保护（5秒超时、destroyForcibly 防挂起），但 `MyProjectActivity` 用手动 `ProcessBuilder` 重写了一遍。久而久之两处逻辑会不一致。

**修复**：统一使用 `ClaudeCodeClient.isCliAvailable()`，`MyProjectActivity` 不重复实现 CLI 检测逻辑。

**教训**：工具方法统一在一个类中实现，避免跨模块重复。"一处定义，多处调用"。一处定义，多处调用"。

---

### 2.8 Sprint 5 问题与修复

#### 问题 I：22+ 个前端 action 从未被连接

**根因**：CefBrowserPanel.handleJsRequest() 的 when 表达式只处理了 10 个 action，其余 22+ 个（session/search、MCP、skill、agent 等）全部落入 else 分支返回 null。前端调用这些 action 时，Promise 永远 pending，UI 功能看似正常实则完全失效。

**修复**：在 when 中逐一添加所有 action 的 handler 实现（searchSessions、exportSession、MCP 全套、skill 全套、agent 全套等）。

**教训**：前后端接口必须在同一文档中维护——每新增一个 action，Kotlin handler 必须同步实现。"接口契约"不是可选的。

#### 问题 J：SdkPermissionHandler 发布事件但无订阅者

**根因**：SdkPermissionHandler.handlePermissionRequest() 发布 PermissionRequestEvent，但整个代码库中没有任何订阅者。权限对话框永远不会出现，CLI 权限请求被静默拒绝。

**修复**：在 MyProjectActivity 中订阅 PermissionRequestEvent，路由到 InteractiveRequestEngine.askQuestion() 显示权限对话框，用户回答后通过 SdkPermissionHandler.submitDecision() 提交决策。

**教训**：EventBus 事件发布后必须有订阅者。"发布-订阅"模式要求两边都存在，否则事件石沉大海。

#### 问题 K：EventBus 事件类型缺少必要字段

**根因**：PermissionRequestEvent 最初没有 project 字段，但事件处理器需要通过 project 查找 InteractiveRequestEngine 实例。

**修复**：在 PermissionRequestEvent 中添加 project: Project 字段。

**教训**：事件类设计时，考虑订阅者需要什么上下文信息。事件是解耦的，但事件的 data 结构必须包含订阅者所需的全部信息。

#### 问题 L：Kotlin suspend 函数与同步上下文的冲突

**根因**：InteractiveRequestEngine.askQuestion() 是 suspend 函数，必须在协程中调用。但 SdkPermissionHandler.handlePermissionRequest() 在 IO 线程上同步调用，不能直接 suspend。

**修复**：使用 EventBus 解耦——handlePermissionRequest() 同步发布事件后立即返回，异步处理通过事件总线派发到协程中。

**教训**：IDE 插件中有多种执行上下文（EDT、IO 线程、协程）。suspend 函数不能从非协程上下文直接调用，必须通过事件总线或 scope.launch 派发到协程。

当 Bridge 通信不工作时，按顺序执行以下检查：

#### JS→Java 通道
- [ ] `window.ccBackend` 是否存在？（`injectBackendJavaScript()` 是否被调用）
- [ ] `window.javaRequestCallback` 是否已设置？（JBCefJSQuery iframe 查找是否成功）
- [ ] `javaBridge.invoke()` 是否被调用？（前端 console 有无 `[MockBackend] send:` 日志）
- [ ] Kotlin `handleJsRequest()` 是否触发？（IDEA console 有无 `=== ACTION: xxx ===`）
- [ ] `BridgeManager.sendMessage()` 是否调用了 `claude` CLI？（日志中有无 CLI 命令输出）

#### Java→JS 通道
- [ ] `StreamingOutputEngine.setCefPanel()` 是否被调用？（`cefPanel` 是否为 null）
- [ ] `sendToJavaScript()` 的事件名是否与 JS EventBus 常量完全一致？
- [ ] `sendToJavaScript()` 的 data 格式是否与 JS 期望的一致？
- [ ] `window.ccEvents.emit()` 是否触发？（CEF DevTools console 有无输出）
- [ ] 前端 `eventBus.on(Events.STREAMING_CHUNK, handler)` 是否正确订阅？

#### 流式显示
- [ ] `streamingStore.startStreaming(messageId)` 是否被调用？
- [ ] `streaming:chunk` 事件的 `messageId` 与 `startStreaming()` 传入的是否一致？
- [ ] `StreamingMessage` 组件是否正确订阅了 `Events.STREAMING_CHUNK`？

---

## 三、架构决策记录（ADR）

### ADR-001：采用 JCEF 内嵌浏览器而非原生 Swing

**背景**：需要展示富文本聊天界面、Markdown 渲染、代码高亮，TailwindCSS 组件化开发更高效。

**决策**：前端使用 React + TypeScript + TailwindCSS，通过 JCEF 内嵌到 IDE。

**替代方案对比**：

| 方案 | 优势 | 劣势 |
|------|------|------|
| JCEF (当前) | 富文本、现代化 UI、并行开发 | 启动开销 (~2MB JAR)、部分 IDE 版本兼容问题 |
| 原生 Swing | 零额外开销、与 IDE 深度绑定 | 开发效率低、样式难统一 |
| JavaFX WebView | 现代浏览器引擎 | 需要额外依赖、不在所有 IDE 中可用 |

**结论**：JCEF 是 IntelliJ Platform 官方推荐的嵌入式浏览器方案，收益远超成本。

**后续影响**：需维护前端构建流水线（Vite），且要考虑 JCEF 版本与 IDE 版本的兼容性问题。

---

### ADR-002：HSL 格式的 CSS 变量作为主题切换机制

**背景**：TailwindCSS 3 的 `hsl(var(--xxx))` 机制要求 CSS 变量必须是 HSL 值（空格分隔）。

**决策**：
1. 所有颜色在 `ThemeConfig` 中以 HEX 存储（便于主题配置面板编辑）
2. `applyTheme()` 运行时通过 `hexToHsl()` 转换为 HSL 字符串
3. 设置到 `document.documentElement.style`
4. `globals.css` 中定义 `:root`（亮色）和 `.dark`（暗色）两套变量作为默认值

**关键代码**：
```ts
root.style.setProperty('--primary', hexToHsl(colors.primary));
root.setAttribute('data-theme', theme.isDark ? 'dark' : 'light');
```

**权衡**：
- HEX→HSL 转换有微小 CPU 开销（可忽略）
- 设计文档和主题配置存储 HEX，运行时转换，职责分离清晰

---

### ADR-003：自定义 iframe Bridge 而非直接使用 JBCefJSQuery

**背景**：JBCefJSQuery 内部生成的函数名是随机的（`__jcef_query_<randomId>__`），无法从外部直接引用。

**决策**：
1. 创建自定义隐藏 iframe，加载 data URL，内嵌 JS 脚本
2. 主页面 bridge（`window.ccBackend.send()`）通过 `postMessage` 发送请求到 iframe
3. iframe 收到后调用 `window.javaRequestCallback`
4. `window.javaRequestCallback` 通过遍历 `document.querySelectorAll('iframe')` 查找 JBCefJSQuery iframe，并调用其 `__jcef_query_<id>__` 函数

**代码片段**：
```kotlin
// 注入隐藏 iframe
b.getCefBrowser().executeJavaScript("""
    var iframe = document.createElement('iframe');
    iframe.src = 'data:text/html;charset=utf-8,' + encodeURIComponent(html);
    iframe.style.display = 'none';
    document.body.appendChild(iframe);
""")

// ccBackend.send() 实现
window.ccBackend = {
    send: function(request) {
        var iframe = document.getElementById('__cc_bridge_iframe__');
        iframe.contentWindow.postMessage({type:'cc-java-request', request: JSON.stringify(request)}, '*');
    }
};
```

**权衡**：比直接使用 JBCefJSQuery 多一层间接，但提供了：
- 明确的 `window.ccBackend` 和 `window.ccEvents` API
- 灵活的 `ccEvents.emit()` 事件机制
- 可在 dev 模式用 mock bridge 替换（前端独立开发）

---

### ADR-004：StreamCallback + onResponse 双通道模式

**背景**：streaming 场景下，数据分多次通过事件推送（`streaming:chunk`），但 Promise 最终需要一次 `response` 事件来 resolve/reject。

**决策**：
```kotlin
// BridgeManager.sendMessage()
bridgeManager.sendMessage(
    message = content,
    sessionId = sessionId,
    callback = object : StreamCallback {
        override fun onLineReceived(line: String) {
            sendToJavaScript("streaming:chunk", mapOf("chunk" to line))
        }
    },
    onResponse = { result, error ->
        // streaming 结束后，resolve 前端 Promise
        sendResponseToJs("sendMessage", queryId, result, error)
    }
)
```

**权衡**：
- 两套机制（callback 用于 streaming 事件，onResponse 用于 Promise）略显冗余
- 但职责清晰：callback 处理实时推送，onResponse 处理最终结果

**替代方案**：
- 所有结果都通过 `ccEvents.emit()` 推送，由前端根据 `queryId` 路由 → 已被否决（前端路由逻辑复杂，且 `queryId` 是数字，不好作为事件名的一部分）

---

### ADR-005：Kotlin 2.1.20 而非最新版

**背景**：Kotlin 2.3.20 在 IDEA 插件环境出现 coroutines Debug metadata 版本不匹配崩溃。

**决策**：降级到 Kotlin 2.1.20（IDEA 内置的 Kotlin 版本）。

**权衡**：
- 放弃 Kotlin 2.3 的新语言特性
- 但获得 IDE 内置 Kotlin 的稳定性和零兼容负担

**触发条件**：若后续 IDEA 版本升级到内置 Kotlin 2.2+，可尝试升级。

---

## 四、未来迭代指南

### 4.1 新功能引入检查流程

引入任何新功能时，必须执行以下检查：

#### Bridge 契约检查
- [ ] 新增 action 时，在 `handleJsRequest()` 的 `when` 表达式中添加分支
- [ ] 若需要 Java→JS 推送，确认事件名符合 ADR-003 规范（与 JS EventBus 常量一致）
- [ ] 若需要响应事件，确认在 handler 结束时调用 `sendResponseToJs()`
- [ ] 在 mock-bridge.ts 中添加对应的 mock 实现

#### 前端集成检查
- [ ] 新增事件订阅时，在 `useEffect` cleanup 中取消订阅
- [ ] 新增 CSS 变量时，在 `globals.css` 的 `:root` 和 `.dark` 中同时定义
- [ ] 若新增 Tailwind 颜色，在 `tailwind.config.js` 中添加映射
- [ ] 运行 `npm run dev` 验证 UI 效果

#### 端到端检查
- [ ] `./gradlew buildPlugin` 构建成功
- [ ] `./gradlew runIde` 可启动，Tool Window 正常加载
- [ ] 新功能在 IDE 中完整走一遍（触发条件 → 数据流 → UI 渲染）

### 4.2 应避免的陷阱

#### 陷阱 1：在 handleJsRequest() 中同步调用 sendResponseToJs()

**错误**：
```kotlin
// 错误：streaming 回调是异步的，在 streamMessage() 中立即返回 null
private fun handleStreamMessage(queryId: Int, params: JsonElement?): Any? {
    bridgeManager.streamMessage(...) { callback ->
        // streaming 数据通过 callback.onLineReceived 推送
    }
    return null  // 此时 streaming 还未结束！
}
```

**正确**：`onResponse` 在 streaming 结束后由 BridgeManager 调用，确保 Promise 被 resolve。

#### 陷阱 2：忘记调用 `setCefPanel()`

`StreamingOutputEngine` 若未注入 `CefBrowserPanel` 引用，所有 Java→JS 推送都会静默丢失。

**防御**：在 `MyToolWindowFactory.createToolWindowContent()` 中，初始化 cefPanel 后立即调用：
```kotlin
StreamingOutputEngine.getInstance(project).setCefPanel(cefPanel!!)
```

#### 陷阱 3：CSS 变量名与 Tailwind 默认名冲突

Tailwind 有预留的 token 名（`primary`, `background`, `foreground` 等）。自定义变量使用前缀区分：
- Tailwind 标准：`--primary`, `--background`
- 自定义消息气泡：`--userMessage`, `--aiMessage`（无中划线，表示自定义）

#### 陷阱 4：在 JCEF 回调中直接更新 Swing UI

JCEF 的 JS 回调运行在非 EDT 线程。若回调中更新 Swing 组件（如 Tool Window 标题），需使用：
```kotlin
ApplicationManager.getApplication().invokeLater { /* UI 更新 */ }
```

#### 陷阱 6：LoggerUtils.logger\<T\>() 与 logger\<T\>() 混用

扩展函数 `T.logger()` 和 `LoggerUtils.logger<T>()` 不要混用。统一使用 `logger<T>()` 扩展函数形式。

#### 陷阱 7：React 根组件缺少 ErrorBoundary

JCEF 环境中无法方便地打开浏览器 DevTools 调试控制台，React 渲染异常会导致整页空白，必须有 ErrorBoundary。

**防御**：
```tsx
<ErrorBoundary>
  <JcefBrowser onReady={() => useAppStore.getState().initializeSessions()}>
    <AppRouter />
  </JcefBrowser>
</ErrorBoundary>
```

#### 陷阱 8：JCEF iframe 注入假设 DOM 已就绪

在 `document.body` 可能为 null 时，使用 `document.body ? ... : DOMContentLoaded` 逻辑不可靠——若页面已 `complete`，事件不会再次触发。

**防御**：统一使用 `document.addEventListener('DOMContentLoaded', ...)` 而不依赖即时状态。

#### 陷阱 9：Store 初始化依赖隐式启动

前端 Store 的 `initializeSessions()` 等初始化方法必须显式调用，不能假设"应用启动时自动执行"。

**防御**：在根组件 `JcefBrowser` 的 `onReady` 回调中显式调用。

#### 陷阱 11：外部依赖可用性未在使用前验证

外部依赖（如 CLI）在使用时才被发现不可用，会产生难以理解的系统级错误。

**防御**：在 `BridgeManager` 中，使用前同步检查 `isCliAvailable()`，快速失败并返回用户友好的错误消息。

#### 陷阱 12：重复实现同一工具方法

同一逻辑在多个地方实现，久而久之版本分裂，一处修了 bug 另一处没修。

**防御**：工具方法（如 CLI 检测）统一在单一类中实现，所有调用方通过该类访问。

#### 陷阱 10：混淆前端 EventBus 和 Java 事件系统

**两个独立的事件总线**：
1. **Kotlin EventBus** (`infrastructure/eventbus/EventBus.kt`) — Kotlin 内部模块通信
2. **JS EventBus** (`webview/src/shared/utils/event-bus.ts`) — React 组件通信

Java→JS 推送**只通过** `CefBrowserPanel.sendToJavaScript()` → `window.ccEvents.emit()` → JS EventBus，不经过 Kotlin EventBus。

### 4.3 新增 API action 的标准模板

```kotlin
// 1. 在 CefBrowserPanel.handleJsRequest() 的 when 中添加分支
"myAction" -> handleMyAction(queryId, params)

// 2. 实现 handler（同步或异步）
private fun handleMyAction(queryId: Int, params: JsonElement?): Any? {
    val input = params?.asJsonObject?.get("input")?.asString ?: return null
    val result = myService.doSomething(input)

    // 3. 同步返回：直接通过 sendResponseToJs 返回
    sendResponseToJs("myAction", queryId, result)
    return null  // 重要：返回 null 表示响应已通过 sendResponseToJs 发送
}

// 4. 若需要异步回调（如 streaming）
private fun handleMyStreamingAction(queryId: Int, params: JsonElement?): Any? {
    myService.streamAction(params, object : StreamCallback {
        override fun onLineReceived(line: String) {
            sendToJavaScript("streaming:chunk", mapOf("chunk" to line))
        }
        override fun onStreamComplete(messages: List<String>) {
            sendToJavaScript("streaming:complete", mapOf("messages" to messages))
            // 5. streaming 结束后 resolve Promise
            sendResponseToJs("myStreamingAction", queryId, mapOf("done" to true))
        }
        override fun onStreamError(error: String) {
            sendToJavaScript("streaming:error", mapOf("error" to error))
            sendResponseToJs("myStreamingAction", queryId, null, error)
        }
    })
    return null
}
```

### 4.4 模块依赖约束

为避免循环依赖，遵守以下分层约束：

```
[前端 UI 层]     → [前端 Store/Hooks] → [javaBridge] → [Kotlin Bridge 层]
                                                            ↓
[工具类/Utils] ←←←←←←←←←←←←←←←←←←←←←←←←←← [业务 Service 层]
                                                            ↓
                                                    [基础设施层]
```

**强制约束**：
- 业务 Service（如 `ChatOrchestrator`）不直接引用 `CefBrowserPanel`
- `StreamingOutputEngine` 只通过 `setCefPanel()` 注入引用（单向）
- 前端不引用任何 Kotlin 类（完全解耦）

---

## 五、文件速查索引

| 目的 | 文件路径 |
|------|---------|
| JCEF Bridge 核心 | `src/main/kotlin/.../browser/CefBrowserPanel.kt` |
| 消息发送入口 | `src/main/kotlin/.../bridge/BridgeManager.kt` |
| 流式输出引擎 | `src/main/kotlin/.../application/streaming/StreamingOutputEngine.kt` |
| CLI 客户端 | `src/main/kotlin/.../adaptation/sdk/ClaudeCodeClient.kt` |
| 前端 Bridge 封装 | `webview/src/lib/java-bridge.ts` |
| 前端事件总线 | `webview/src/shared/utils/event-bus.ts` |
| 流式 Hook | `webview/src/shared/hooks/useStreaming.ts` |
| 主题 Store | `webview/src/shared/stores/themeStore.ts` |
| Tailwind 配置 | `webview/tailwind.config.js` |
| CSS 变量定义 | `webview/src/styles/globals.css` |
| Mock Bridge（dev） | `webview/src/dev/mock-bridge.ts` |
| 工具窗口工厂 | `src/main/kotlin/.../toolWindow/MyToolWindowFactory.kt` |

---

*本文档随插件版本迭代持续更新。每次重大架构变更后，应同步更新本手册相关章节。*
