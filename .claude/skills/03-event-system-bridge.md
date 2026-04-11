# 前后端事件系统桥接

## 问题背景
项目存在两套独立的事件系统：
1. **Java 注入的 window.ccEvents** — 简单的 on/off/emit 实现
2. **前端 React 的 eventBus** — 独立的单例，支持 once/clear 等

Java 调用 sendToJavaScript() → window.ccEvents.emit()，但前端 hook 监听的是 eventBus，两者无连接。

## 架构
```
Java sendToJavaScript()
  → window.ccEvents.emit("streaming:chunk", data)
    → window.dispatchEvent(new CustomEvent("streaming:chunk", {detail: data}))
      → window.addEventListener("streaming:chunk", handler) // index.tsx 中注册
        → eventBus.emit("streaming:chunk", data) // 转发到前端
          → useStreaming hook 接收处理
```

## 实现要点

### 后端（CefBrowserPanel.kt 注入脚本）
ccEvents.emit 必须同时 dispatch CustomEvent：
```javascript
emit: function(event, data) {
    // 1. 调用本地 handlers
    if (this.handlers[event]) {
        this.handlers[event].forEach(function(h) { h(data); });
    }
    // 2. 桥接到 CustomEvent
    window.dispatchEvent(new CustomEvent(event, { detail: data }));
}
```

### 前端（index.tsx 模块加载时）
```typescript
const javaEvents = [
  Events.STREAMING_CHUNK,
  Events.STREAMING_COMPLETE,
  Events.STREAMING_ERROR,
  'response',
  'question',
];
javaEvents.forEach((eventName) => {
  window.addEventListener(eventName, (e: Event) => {
    const customEvent = e as CustomEvent;
    eventBus.emit(eventName, customEvent.detail);
  });
});
```

## 诊断检查清单
- [ ] 新增事件是否在 index.tsx 的 javaEvents 数组中注册？
- [ ] sendToJavaScript 的事件名是否与 Events 常量一致？
- [ ] ccEvents.emit() 是否同时 dispatch CustomEvent？
- [ ] 事件数据结构是否与前端 hook 期望的格式匹配？

## 关键代码位置
- CefBrowserPanel.kt: injectBackendJavaScript() ~line 995
- index.tsx: line 15-30 (事件桥接注册)
- event-bus.ts: Events 常量定义
- useStreaming.ts: 事件监听
