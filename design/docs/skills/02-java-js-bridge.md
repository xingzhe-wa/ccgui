# Java <-> JavaScript 通信桥接

## 架构概览

```
Frontend (React/TypeScript)              Backend (Kotlin)
┌────────────────────────┐              ┌──────────────────────┐
│  java-bridge.ts        │              │  CefBrowserPanel     │
│  ┌──────────────────┐  │              │                      │
│  │ invoke(action,   │  │              │  setupJsQuery()      │
│  │   params)        │──┼──postMessage──┼─► handleJsRequest()  │
│  │   → Promise<T>  │  │  (via iframe) │    → action routing  │
│  └──────────────────┘  │              │    → response/event  │
│                        │              │                      │
│  java-bridge.ts        │              │  sendToJavaScript()  │
│  ┌──────────────────┐  │              │  ┌────────────────┐  │
│  │ ccEvents.on(     │◄─┼──executeJS───┼──│ ccEvents.emit()│  │
│  │   'response',    │  │  (event+data)│  └────────────────┘  │
│  │   handler)       │  │              │                      │
│  └──────────────────┘  │              └──────────────────────┘
└────────────────────────┘
```

### 通信通道

| 方向 | 机制 | 说明 |
|------|------|------|
| JS -> Java | 隐藏 iframe + postMessage + `__jcef_query_*` | 前端 `window.ccBackend.send()` 通过 iframe 中转调用 JBCefJSQuery |
| Java -> JS | `executeJavaScript()` + `window.ccEvents.emit()` | Kotlin 直接执行 JS，通过 ccEvents 分发事件 |

## JS -> Java 通信

### 请求格式

前端 `java-bridge.ts` 发送的请求结构：

```json
{
  "queryId": 1,
  "action": "sendMessage",
  "params": { "message": "..." }
}
```

- `queryId`：自增计数器，用于匹配请求和响应（Promise resolve/reject）
- `action`：字符串，映射到 `CefBrowserPanel.handleJsRequest()` 中的 when 分支
- `params`：对象或基本类型，具体结构取决于 action

### 关键坑点：参数嵌套

`ChatView.tsx` 中的发送逻辑将多个字段序列化为单个 JSON 字符串：

```typescript
// ChatView.tsx 第 108 行
javaBridge.sendMessage(
  JSON.stringify({ sessionId: currentSessionId, content: fullContent, messageId: aiMessageId })
);
```

这导致 Kotlin 端收到的 `params` 结构是：

```json
{ "message": "{\"sessionId\":\"...\",\"content\":\"...\",\"messageId\":\"...\"}" }
```

**Java 端不能直接 `params.get("sessionId")`**，需要先取 `params.get("message")` 再 JSON.parse：

```kotlin
// CefBrowserPanel.handleSendMessage() -- 正确解析方式
val jsonParams = params.asJsonObject
val messageStr = jsonParams.get("message")?.asString
if (messageStr != null) {
    // 解析内层 JSON
    val payload = JsonUtils.parseObject(messageStr)?.asJsonObject
    sessionId = payload?.get("sessionId")?.asString ?: ""
    content = payload?.get("content")?.asString ?: ""
    messageId = payload?.get("messageId")?.asString ?: ""
} else {
    // 兼容直接字段格式（某些 action 可能直接传字段）
    sessionId = jsonParams.get("sessionId")?.asString ?: ""
    content = jsonParams.get("content")?.asString ?: ""
}
```

### JBCefJSQuery 回调机制

1. `JBCefJSQuery.create(browser)` 创建查询实例，在页面中注入 `__jcef_query_<id>__` 全局函数
2. `jsQuery.addHandler(jsQueryInvoker!!)` 注册回调
3. `injectBackendJavaScript()` 的 Step 3 通过轮询找到 `__jcef_query_*` 并设置为 `window.javaRequestCallback`
4. 前端调用链：`window.ccBackend.send(req)` -> iframe postMessage -> `window.javaRequestCallback(req)` -> `__jcef_query_*(req)` -> Kotlin `handleJsRequest()`
5. `handleJsRequest()` 返回 `JBCefJSQuery.Response("")`（空字符串），实际响应通过 `sendToJavaScript("response", data)` 异步发送

**注意**：`handleJsRequest()` 返回的 `Response` 内容会被 JBCefJSQuery 同步返回给 iframe，但 Bridge 丢弃了该返回值。所有业务响应走 `sendToJavaScript` 事件通道。

### 异步 Action 的处理

部分 action（如 `handleExecuteSkill`、`handleOptimizePrompt`）需要异步执行：

```kotlin
private fun handleExecuteSkill(queryId: Int, params: JsonElement?): Any? {
    // 返回 submitted: true 表示已接受，实际结果通过事件发送
    scope.launch {
        try {
            val result = skillExecutor.executeSkill(skill, context)
            sendToJavaScript("skill:result", mapOf("skillId" to skillId, "result" to result.toString()))
        } catch (e: Exception) {
            sendToJavaScript("skill:error", mapOf("skillId" to skillId, "error" to e.message))
        }
    }
    return mapOf("submitted" to true)
}
```

对于需要 resolve 前端 Promise 的异步 action，使用 `onResponse` 回调：

```kotlin
private fun handleSendMessage(queryId: Int, params: JsonElement?): Any? {
    bridgeManager.sendMessage(
        message = content,
        sessionId = sessionId,
        callback = object : StreamCallback { ... },
        // 流结束后通过 'response' 事件 resolve 前端 Promise
        onResponse = { result, error ->
            sendResponseToJs("sendMessage", queryId, result, error)
        }
    )
    return null  // null = 不立即响应，等待 onResponse
}
```

## Java -> JS 通信

### sendToJavaScript 实现

```kotlin
fun sendToJavaScript(event: String, data: Map<String, Any>) {
    browser?.let { b ->
        val jsonData = JsonUtils.toJson(data)
        // 转义单引号和反斜杠，防止 JS 注入
        val safeEvent = event.replace("\\", "\\\\").replace("'", "\\'")
        val safeJsonForJs = jsonData
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        val js = "window.ccEvents && window.ccEvents.emit('$safeEvent', JSON.parse('$safeJsonForJs'));"
        b.getCefBrowser().executeJavaScript(js, b.getCefBrowser().getURL(), 0)
    }
}
```

### 事件名称约定

| 事件 | 触发场景 | 数据结构 |
|------|---------|---------|
| `response` | 通用请求响应 | `{queryId, result?, error?}` |
| `streaming:chunk` | 流式输出增量 | `{messageId, chunk}` |
| `streaming:complete` | 流式输出完成 | `{messageId, messages[]}` |
| `streaming:error` | 流式输出错误 | `{messageId, error}` |
| `skill:result` | Skill 执行完成 | `{skillId, result}` |
| `skill:error` | Skill 执行失败 | `{skillId, error}` |
| `agent:result` | Agent 执行完成 | `{agentId, result}` |
| `agent:error` | Agent 执行失败 | `{agentId, error}` |
| `mcp:serverStatus` | MCP Server 状态变更 | `{serverId, success}` |
| `mcp:testResult` | MCP Server 测试结果 | `{serverId, success, result}` |

### 坑点：JSON 转义

`sendToJavaScript` 将 JSON 嵌入 JS 单引号字符串，**必须转义以下字符**：

| 字符 | 转义 | 原因 |
|------|------|------|
| `\` | `\\` | 反斜杠会破坏后续转义序列 |
| `'` | `\'` | 单引号会截断 JS 字符串 |
| `\n` | `\\n` | 换行符会中断 JS 语句 |
| `\r` | (删除) | 回车符无意义且可能干扰 |

如果未正确转义，会导致：
- JS 语法错误（`executeJavaScript` 静默失败）
- 意外的 JS 代码执行（安全风险）
- 前端 JSON.parse 抛异常

### 前端响应接收

前端 `java-bridge.ts` 通过两种机制接收 Java 响应：

1. **ccEvents 拦截器**（由 `injectBackendJavaScript()` 注入）：
   ```javascript
   // ccEvents.emit 被代理，'response' 事件自动路由到 pendingRequests
   window.ccEvents.emit = function(event, data) {
       if (event === 'response' && data && data.queryId) {
           var cb = pendingRequests[data.queryId];
           if (cb) { delete pendingRequests[data.queryId]; cb(data.result); }
       }
       return origEmit(event, data);
   };
   ```

2. **java-bridge.ts init() 监听**：
   ```typescript
   // java-bridge.ts init()
   this.responseHandler = window.ccEvents.on('response', (data: any) => {
       const { queryId, result, error } = data;
       const pending = this.pendingRequests.get(queryId);
       if (pending) {
           if (error) pending.reject(new Error(error));
           else pending.resolve(result);
           this.pendingRequests.delete(queryId);
       }
   });
   ```

**注意**：ccEvents 拦截器和 java-bridge.ts 监听器都会触发，但由于 `pendingRequests.delete()` 是幂等的，不会重复处理。

## 新增 Action 的步骤

1. **Kotlin 端**：在 `CefBrowserPanel.handleJsRequest()` 的 `when` 分支添加新的 action
2. **Kotlin 端**：实现对应的 `handleXxx(queryId, params)` 方法
3. **TypeScript 端**：在 `java-bridge.ts` 添加对应的异步方法
4. **TypeScript 端**：如需类型安全，在 `JavaBackendAPI` 接口中添加方法签名
5. **测试**：确认参数解析（嵌套 vs 直接字段格式）和响应格式正确

## 调试检查清单

- [ ] 新增 action 是否在 `handleJsRequest()` 的 when 分支注册？
- [ ] 参数解析是否兼容嵌套 JSON 字符串格式（`params.message` 为 JSON string）？
- [ ] 异步 action 是否通过 `scope.launch` + `sendToJavaScript` 事件回调？
- [ ] `sendToJavaScript` 的 event 名称和 data 结构是否与前端 `java-bridge.ts` 监听一致？
- [ ] JSON 数据中是否包含未转义的单引号或反斜杠？
- [ ] `sendToJavaScript` 是否使用 `executeJavaScript()` 而非 `loadURL("javascript:...")`？
- [ ] 前端 `invoke()` 是否有 30 秒超时保护？
- [ ] `queryId` 是否正确传递，确保 Promise 能被 resolve/reject？

## 关键代码位置

| 文件 | 方法 / 区域 | 职责 |
|------|------------|------|
| `CefBrowserPanel.kt` | `setupJsQuery()` | 创建 JBCefJSQuery，注册 handleJsRequest 回调 |
| `CefBrowserPanel.kt` | `handleJsRequest()` | 解析 action，路由到具体 handler |
| `CefBrowserPanel.kt` | `sendResponseToJs()` | 通过 'response' 事件发送结果到前端 |
| `CefBrowserPanel.kt` | `sendToJavaScript()` | 通用 Java->JS 事件推送 |
| `CefBrowserPanel.kt` | `injectBackendJavaScript()` | 注入 ccBackend、ccEvents、javaRequestCallback |
| `java-bridge.ts` | `invoke()` | 封装请求发送 + Promise 管理 |
| `java-bridge.ts` | `init()` | 等待 Bridge 就绪，监听 'response' 事件 |
| `java-bridge.ts` | `sendMessage()` | 发送聊天消息（JSON 字符串格式） |
| `ChatView.tsx` | `handleSend()` | 组装消息参数，调用 javaBridge |
| `streamingStore.ts` | `cancelStreaming()` | 取消流式输出 |
| `event-bus.ts` | `EventBus` + `Events` | 前端内部事件总线及事件常量定义 |
