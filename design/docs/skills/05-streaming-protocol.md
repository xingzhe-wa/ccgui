# 流式通信协议规范

## 消息流程
```
Frontend                    Java Backend              Daemon (Node.js)
  │                            │                          │
  │──sendMessage(msgId+data)→│                          │
  │                            │──stdin NDJSON──→         │
  │                            │                          │──claude SDK
  │                            │←──stdout NDJSON─        │
  │←streaming:chunk{msgId}───│  (逐行回调)              │
  │←streaming:chunk{msgId}───│                          │
  │←streaming:complete{msgId}│                          │
  │←response{queryId}────────│                          │
```

## 核心规则：messageId 必须贯穿全链路

### 前端发送
```typescript
const aiMessageId = `msg-${Date.now()}-ai`;
startStreaming(aiMessageId); // 开始监听该 ID 的事件
javaBridge.sendMessage(JSON.stringify({
  sessionId: currentSessionId,
  content: fullContent,
  messageId: aiMessageId  // ← 必须携带
}));
```

### Java 转发
```kotlin
// 解析前端传入的 messageId
val payload = JsonUtils.parseObject(messageStr)?.asJsonObject
val messageId = payload?.get("messageId")?.asString ?: "default"

// 流式回调中携带 messageId
override fun onLineReceived(line: String) {
    sendToJavaScript("streaming:chunk", mapOf(
        "messageId" to contextMessageId,  // ← 必须回传
        "chunk" to line
    ))
}
```

### 前端接收
```typescript
eventBus.on(Events.STREAMING_CHUNK, (data) => {
  if (data.messageId === messageId) {  // 按 messageId 过滤
    contentRef.current += data.chunk;
  }
});
```

## 事件数据格式规范

| 事件名 | 数据结构 |
|--------|----------|
| streaming:chunk | { messageId: string, chunk: string } |
| streaming:complete | { messageId: string, messages: string[] } |
| streaming:error | { messageId: string, error: string } |
| response | { queryId: number, result?: any, error?: string } |
| streaming:question | { questionId: string, questionType: string, message: string, options: [], required: boolean } |

## 坑点
1. 不带 messageId → 前端 useStreaming 永远不匹配 → 消息不显示
2. Java 端 messageId 默认 "default" → 多消息并发时串台
3. sendMessage 参数是嵌套 JSON → 必须两层解析

## 关键代码位置
- ChatView.tsx: handleSendMessage()
- CefBrowserPanel.kt: handleSendMessage(), handleStreamMessage()
- useStreaming.ts: startStreaming() 事件监听
- java-bridge.ts: sendMessage(), streamMessage()
