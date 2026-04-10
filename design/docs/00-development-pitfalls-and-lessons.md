# CCGUI 项目开发踩坑与经验教训

## 概述

本文档记录 CCGUI（CC Assistant）IntelliJ 插件项目开发过程中遇到的关键技术问题和解决方案。

**目标读者**：项目的后续开发者、新加入团队的成员，以及需要维护或扩展该插件的工程师。

**配套参考**：`design/docs/skills/` 目录下包含各模块的设计文档和 API 说明，建议结合阅读。每个 PIT 条目中的"关键位置"可帮助快速定位相关源码。

---

## 踩坑总览表

| 编号 | 分类 | 问题现象 | 根因 | 影响 | 状态 |
|------|------|----------|------|------|------|
| PIT-001 | JCEF 时序 | UI 白屏不渲染 | loadURL 异步但注入 JS 在其之后立即执行 | 前端 Bridge 未初始化，所有通信失败 | resolved |
| PIT-002 | 构建配置 | 生产环境白屏 | Vite 默认 base:'/' 生成绝对路径 | file:// URL 无法加载 JS/CSS 资源 | resolved |
| PIT-003 | 前后端通信 | 流式输出不显示 | Java 注入的 ccEvents 与前端 eventBus 无桥接 | 流式消息丢失，UI 无实时反馈 | resolved |
| PIT-004 | 协程调度 | 插件启动崩溃 | Dispatchers.Main 在 postStartupActivity 阶段未就绪 | PluginException 导致插件无法加载 | resolved |
| PIT-005 | 协议设计 | 流式消息不更新 UI | streaming 事件缺少 messageId 字段 | 前端按 messageId 过滤后永远不匹配 | resolved |
| PIT-006 | 参数解析 | 发送消息无响应 | handleSendMessage 少解析一层 JSON 嵌套 | 用户发送的消息无法到达后端处理 | resolved |
| PIT-007 | ID 一致性 | 事件推送失败 | ToolWindow ID 在代码与 plugin.xml 中不一致 | getToolWindow() 返回 null | resolved |
| PIT-008 | 命名规范 | 侧边栏显示错误名称 | 插件 ID 与显示名未分离管理 | 用户看到 "CCGUI" 而非 "CC Assistant" | resolved |

---

## PIT-001: JCEF 页面加载时序

**现象**：插件启动后 ToolWindow 显示空白页面，所有 UI 均不渲染。

**根因**：`injectBackendJavaScript()` 在 `loadURL()` 之后通过 `EventQueue.invokeLater` 调用，但 `loadURL()` 是异步操作——页面 HTML 尚未加载完成时，注入的 JS 执行于空 DOM 之上，`window.ccBackend` 和 `window.ccEvents` 均未生效。

**解决方案**：

1. 添加 `CefLoadHandler` 反射监听器，通过 `onLoadingStateChange` 回调检测页面加载完成（`isLoading == false`）。
2. 加载完成的回调队列 `loadListeners` 中缓存待注入任务，onLoadEnd 时依次执行。
3. 增加 3 秒后备超时，防止 load handler 未触发的极端情况。

**关键位置**：`CefBrowserPanel.kt` — `loadHtmlPage()` (line ~1145), `setupLoadListener()` (line ~131)

**状态**：resolved

---

## PIT-002: Vite 构建路径与 file:// 不兼容

**现象**：开发环境正常，但生产构建后的插件 UI 白屏，控制台 404 错误加载资源。

**根因**：Vite 默认 `base: '/'`，构建出的 HTML 中引用路径为 `/assets/xxx.js`（绝对路径）。当 JCEF 通过 `file:///path/to/dist/index.html` 加载时，浏览器将 `/assets/xxx.js` 解析为 `file:///assets/xxx.js`（磁盘根目录），而非 dist 目录下的相对路径。

**解决方案**：`vite.config.ts` 中将 `base` 改为 `'./'`，使构建产物使用相对路径 `./assets/xxx.js`。

```typescript
// webview/vite.config.ts line 8
base: './',
```

**关键位置**：`webview/vite.config.ts` line 8

**状态**：resolved

---

## PIT-003: 前后端事件系统双层脱节

**现象**：Java 后端发送的流式事件（streaming:chunk 等）在前端 UI 中无任何显示。

**根因**：Java 通过 `sendToJavaScript()` 调用 `window.ccEvents.emit()`，但前端 React hook（如 `useStreaming`）监听的是 `eventBus` 实例（`@/shared/utils/event-bus`），这两个是完全独立的事件系统——Java 注入的 `ccEvents` 是全局 window 上的对象，而 `eventBus` 是 ES Module 内部实例，二者之间没有任何桥接。

**解决方案**：

1. Java 端 `ccEvents.emit()` 内部同时调用 `window.dispatchEvent(new CustomEvent(event, { detail: data }))`，将事件广播到 DOM。
2. 前端 `index.tsx` 中通过 `window.addEventListener()` 监听这些 CustomEvent，将 `customEvent.detail` 转发到 `eventBus.emit()`。

```typescript
// webview/src/main/index.tsx line 15-30
javaEvents.forEach((eventName) => {
  window.addEventListener(eventName, (e: Event) => {
    const customEvent = e as CustomEvent;
    eventBus.emit(eventName, customEvent.detail);
  });
});
```

**关键位置**：`CefBrowserPanel.kt` `injectBackendJavaScript()` (line ~1043), `webview/src/main/index.tsx` (line 15-30)

**状态**：resolved

---

## PIT-004: Kotlin 协程 Dispatchers.Main 启动崩溃

**现象**：插件启动阶段抛出 `PluginException: CoroutineExceptionHandler` 异常，导致插件无法加载。

**根因**：`Dispatchers.Main` 依赖 IntelliJ 平台的 EDT（Event Dispatch Thread）基础设施，在 `postStartupActivity` 回调阶段这些设施可能尚未完全就绪。此时创建以 `Dispatchers.Main` 为调度器的协程会触发平台类加载失败。

**解决方案**：所有 `CoroutineScope` 统一使用 `Dispatchers.Default`，避免在启动阶段依赖 EDT 调度。仅在明确需要 UI 操作时通过 `withContext(Dispatchers.EDT)` 切换。

```kotlin
// EventBus.kt, CefBrowserPanel.kt, MyProjectActivity.kt 等
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

**关键位置**：`EventBus.kt` line 34, `CefBrowserPanel.kt` line 45, `MyProjectActivity.kt` line 25

**状态**：resolved

---

## PIT-005: 流式事件缺少 messageId

**现象**：Claude CLI 返回的流式输出无法更新到 UI 上的对应消息气泡。

**根因**：Java 后端发送 `streaming:chunk` 事件时只包含 `{chunk: line}`，但前端 `useStreaming` hook 按 `messageId` 过滤事件。由于事件中没有 `messageId` 字段，过滤条件永远不满足，导致流式内容被丢弃。

**解决方案**：

1. 前端 `sendMessage` 时在 payload 中附加 `messageId` 字段（即 AI 消息的 UUID）。
2. Java 端 `handleSendMessage` 解析 `messageId` 并保存到上下文中。
3. 所有后续的 `streaming:chunk`、`streaming:complete`、`streaming:error` 事件均携带该 `messageId`。

```kotlin
// CefBrowserPanel.kt — 流式事件携带 messageId
sendToJavaScript("streaming:chunk",
    mapOf("messageId" to contextMessageId, "chunk" to line))
```

**关键位置**：`CefBrowserPanel.kt` `handleSendMessage()` (line ~292-307), `ChatView.tsx` (line ~99-109)

**状态**：resolved

---

## PIT-006: sendMessage 参数嵌套 JSON 解析失败

**现象**：用户在聊天框输入消息并发送后，后端无任何响应，无日志输出。

**根因**：前端 `java-bridge.ts` 的 `sendMessage` 方法通过 `this.invoke('sendMessage', { message })` 发送，Kotlin 端 `handleSendMessage` 接收到的 `params` 结构为 `{message: "{\"sessionId\":\"...\",\"content\":\"...\"}"}`——即 `message` 字段的值是一个 JSON 字符串。但原始实现直接从 `params` 取 `sessionId`/`content`，少了一层 `JSON.parse` 解析。

**解决方案**：`handleSendMessage` 先检查是否存在 `message` 字段，若存在则先 `JSON.parse` 得到实际 payload，再从中提取各字段；同时也兼容直接字段格式作为 fallback。

```kotlin
// CefBrowserPanel.kt handleSendMessage() line ~292
val messageStr = jsonParams.get("message")?.asString
if (messageStr != null) {
    val payload = JsonUtils.parseObject(messageStr)?.asJsonObject
    sessionId = payload?.get("sessionId")?.asString ?: ""
    content = payload?.get("content")?.asString ?: ""
    messageId = payload?.get("messageId")?.asString ?: ""
}
```

**关键位置**：`CefBrowserPanel.kt` `handleSendMessage()` (line ~292-313), `webview/src/lib/java-bridge.ts` (line 100-101)

**状态**：resolved

---

## PIT-007: ToolWindow ID 与 plugin.xml 不一致

**现象**：事件推送和 ToolWindow 获取失败，日志中出现 warn "tool window not found"。

**根因**：`MyProjectActivity.kt` 中通过 `toolWindowManager.getToolWindow("ClaudeCodeJet")` 查找 ToolWindow，但 `plugin.xml` 中注册的 ToolWindow id 为 `"CCGUI"`。ID 不匹配导致 `getToolWindow()` 返回 null。

**解决方案**：全局统一使用 `"CCGUI"` 作为内部 ToolWindow ID，将 `MyProjectActivity.kt` 中的查找字符串从 `"ClaudeCodeJet"` 改为 `"CCGUI"`。

```kotlin
// MyProjectActivity.kt line 87
val toolWindow = toolWindowManager.getToolWindow("CCGUI")
```

**关键位置**：`MyProjectActivity.kt` line 87, `plugin.xml` line 15

**状态**：resolved

---

## PIT-008: 插件显示名散落各处不一致

**现象**：IDE 侧边栏和标题栏显示 "CCGUI" 而非友好的产品名称 "CC Assistant"。

**根因**：`plugin.xml` 中的 `<name>` 标签同时承载了内部 ID 和显示名两个职责。ToolWindow 的标题默认取自其 `id` 属性而非 `<name>`，且代码中多处注释和日志硬编码了 `"CCGUI"` 字符串。

**解决方案**：

1. `plugin.xml` 中 `<name>` 改为 `CC Assistant`。
2. `MyToolWindowFactory.kt` 中显式调用 `toolWindow.setTitle("CC Assistant")`。
3. 全局搜索替换注释和日志中的硬编码名称。

```xml
<!-- plugin.xml -->
<name>CC Assistant</name>
```

```kotlin
// MyToolWindowFactory.kt line 39
toolWindow.setTitle("CC Assistant")
```

**关键位置**：`plugin.xml` line 4, `MyToolWindowFactory.kt` line 39

**状态**：resolved

---

## 经验教训总结

以下是跨模块的通用经验，适用于项目后续所有迭代：

### 1. JCEF 中 loadURL 是异步的，注入 JS 必须等 onLoadEnd

JCEF 的 `loadURL()` / `loadHTML()` 方法都是异步操作，立即返回不代表页面已加载完成。所有依赖 DOM 的 JavaScript 注入必须在页面加载完成回调（`CefLoadHandler.onLoadingStateChange`）中执行，并建议增加合理的后备超时（如 3 秒）防止回调丢失。

### 2. file:// URL 加载资源必须用相对路径

当通过 `file://` 协议加载前端资源时，浏览器对绝对路径的解析行为与 HTTP 不同——`/assets/xxx.js` 会被解析到磁盘根目录。Vite/Webpack 等构建工具的 `base` 配置必须设为 `'./'`（相对路径），而非默认的 `'/'`。

### 3. 前后端事件系统必须有显式桥接，不能假设全局共享

IntelliJ 插件的 Java/Kotlin 后端与 JCEF 中运行的 React 前端运行在不同的 JS 上下文中。通过 `injectBackendJavaScript()` 注入的全局对象（如 `window.ccEvents`）与前端 ES Module 内部的事件实例（如 `eventBus`）是完全隔离的。必须在二者之间建立显式桥接（推荐 CustomEvent + window.addEventListener 方案）。

### 4. IntelliJ 协程避免 Dispatchers.Main 在启动阶段使用

IntelliJ 平台在 `postStartupActivity` 阶段，部分 EDT 相关基础设施可能未完全就绪。使用 `Dispatchers.Main` 创建协程可能导致类加载异常。建议统一使用 `Dispatchers.Default`，仅在需要操作 Swing 组件时通过 `withContext(Dispatchers.EDT)` 临时切换。

### 5. 流式通信协议必须包含请求标识符用于关联

流式场景下（如 Claude CLI 的 stdout 逐行输出），前端需要将每个 chunk 关联到特定的消息。协议设计中必须包含 `messageId`（或类似的请求标识符），从前端发起请求时生成并传递给后端，后端在每个流式事件中回传该标识符，前端据此过滤和更新对应的 UI 状态。

### 6. 插件 ID、Action ID、显示名三者职责不同，必须分离管理

IntelliJ 插件中存在三种标识符，职责各不相同：

- **插件/组件 ID**（如 `"CCGUI"`）：内部标识，用于 `getToolWindow()`、`ActionManager` 等程序化查找，全局唯一且不应随意变更。
- **Action ID**（如 `"CCGUI.CodeExplain"`）：用于菜单和快捷键注册的完整限定 ID。
- **显示名**（如 `"CC Assistant"`）：面向用户的产品名称，显示在 ToolWindow 标题栏、菜单项等位置。

三者的命名空间应独立维护，避免将内部 ID 直接暴露给用户。

### 7. 前后端接口参数结构必须文档化并保持同步

Java 后端 `handleSendMessage` 与前端 `java-bridge.ts` 之间的参数传递存在多层封装（invoke -> message wrapper -> JSON string -> parse）。这种嵌套结构极易导致解析失败（如 PIT-006）。建议：

- 在 `design/docs/skills/` 中维护前后端接口的参数结构文档。
- 使用 TypeScript/Kotlin 的 data class 定义明确的接口契约。
- 在代码注释中标明完整的参数传递链路，包含每层的序列化/反序列化步骤。
