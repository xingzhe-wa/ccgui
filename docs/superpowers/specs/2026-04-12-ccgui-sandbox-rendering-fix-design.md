# cc-gui 沙箱环境渲染修复设计

**日期:** 2026-04-12
**作者:** Claude
**状态:** 设计阶段

## 问题概述

cc-gui 的前端在 JCEF 沙箱环境中无法正常渲染，主要表现为消息列表显示异常、虚拟滚动失效、消息重叠或出现大量空白。

### 核心问题分析

| 问题 | 严重程度 | 位置 |
|------|----------|------|
| Bridge 注入时序竞争 | 高 | `CefBrowserPanel.kt` |
| CSS `contain: strict` 导致渲染异常 | 高 | `MessageList.tsx` |
| 虚拟滚动配置矛盾（禁用测量但引用） | 高 | `MessageList.tsx` |
| iframe + postMessage 三层中转复杂 | 中 | `CefBrowserPanel.kt` |
| 生产构建移除 console.log 无法调试 | 中 | `vite.config.ts` |
| JSON 手动转义风险 | 中 | `CefBrowserPanel.kt` |

## 设计方案：简化架构（方案 B）

### 架构对比

**现有架构（问题）：**
```
JS Frontend → iframe → postMessage → JBCefJSQuery → JsRequestHandler
```

**新架构（简化后）：**
```
JS Frontend → window.ccBackend.send → JBCefJSQuery → JsRequestHandler
```

---

## 第一章：Bridge 架构简化

### 现状问题

cc-gui 当前使用三层中转架构，存在以下问题：

1. **iframe 中转层** - 增加故障点和复杂度
2. **时序竞争** - Bridge 注入与页面加载存在竞争条件
3. **预注册缺失** - React 挂载前的消息会丢失

### 新架构设计

#### 1.1 移除 iframe 中转层

在 `CefBrowserPanel.kt` 中直接注入 `window.ccBackend`：

```kotlin
private fun injectBackendJavaScript() {
    // JBCefJSQuery 注入的函数名，格式为 __jcef_query_<ID>__
    val injectFunction = jsQuery.inject("msg")

    val jsCode = """
        (function() {
            if (window.ccBackend) return; // 防止重复注入

            // 存储注入的 JBCefJSQuery 函数引用
            var _jcefQuery = $injectFunction;

            window.ccBackend = {
                send: function(action, params) {
                    var queryId = Math.random().toString(36).substr(2, 9);
                    var payload = JSON.stringify({
                        queryId: queryId,
                        action: action,
                        params: params
                    });
                    _jcefQuery(payload);
                    return new Promise(function(resolve, reject) {
                        window.ccBackend._pendingRequests[queryId] = { resolve, reject };
                    });
                },
                _pendingRequests: {}
            };
        })();
    """.trimIndent()

    browser?.getCefBrowser()?.executeJavaScript(
        jsCode,
        browser?.getCefBrowser()?.getURL(),
        0
    )
}
```

#### 1.2 实现 Pre-Registration 模式

在 React 挂载前预先注册 `window` 回调，缓存早期消息：

```typescript
// webview/src/main/index.tsx
const pendingMessages: any[] = [];

// 预注册所有可能的回调
if (!window.updateMessages) {
    window.updateMessages = (json: string, sequence?: string | number) => {
        pendingMessages.push({ type: 'updateMessages', json, sequence });
    };
}

if (!window.onStreamStart) {
    window.onStreamStart = () => {
        pendingMessages.push({ type: 'onStreamStart' });
    };
}

if (!window.onContentDelta) {
    window.onContentDelta = (delta: string) => {
        pendingMessages.push({ type: 'onContentDelta', delta });
    };
}

// React 挂载后消费缓存消息
const root = ReactDOM.createRoot(document.getElementById('root')!);
root.render(<App />);

// 在 App.tsx 的 useEffect 中消费
useEffect(() => {
    const consume = () => {
        const messages = (window as any).__consumePendingMessages?.() || [];
        messages.forEach((msg: any) => {
            switch (msg.type) {
                case 'updateMessages':
                    handleUpdateMessages(msg.json, msg.sequence);
                    break;
                case 'onStreamStart':
                    handleStreamStart();
                    break;
                case 'onContentDelta':
                    handleContentDelta(msg.delta);
                    break;
            }
        });
    };
    consume();
}, []);
```

#### 1.3 添加 Bridge 健康检查

```kotlin
private fun startBridgeHealthCheck() {
    scope.launch {
        while (isActive) {
            delay(5000) // 5秒心跳
            val jsCode = """
                if (window.ccBackend && window.ccBackend._healthCheck) {
                    window.ccBackend._healthCheck(new Date().toISOString());
                }
            """.trimIndent()
            browser?.getCefBrowser()?.executeJavaScript(
                jsCode,
                browser?.getCefBrowser()?.getURL(),
                0
            )
        }
    }
}
```

#### 1.4 修复竞争条件

使用 `@Volatile` + `synchronized` 确保 Bridge 单次注入：

```kotlin
@Volatile
private var isBridgeInjected = false

private fun injectBackendJavaScript() {
    synchronized(this) {
        if (isBridgeInjected) return
        isBridgeInjected = true
    }
    // ... 注入逻辑
}
```

### 涉及文件

- `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt`
- `webview/src/main/index.tsx`
- `webview/src/lib/java-bridge.ts`

---

## 第二章：虚拟滚动修复

### 现状问题

`MessageList.tsx` 中：
1. `measureElement: undefined` 禁用动态测量
2. JSX 中引用 `virtualizer.measureElement`
3. `contain: 'strict'` 导致渲染异常

### 修复方案

#### 2.1 启用动态测量

```typescript
const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ESTIMATED_MESSAGE_HEIGHT, // 初始估算
    overscan: OVERSCAN,
    measureElement: (element) => element?.getBoundingClientRect().height, // 启用
});
```

#### 2.2 移除 contain: strict

```typescript
// 删除前
<div
    ref={parentRef}
    className={cn('h-full overflow-y-auto', className)}
    style={{ contain: 'strict' }}
>

// 修改后
<div
    ref={parentRef}
    className={cn('h-full overflow-y-auto', className)}
>
```

#### 2.3 添加内容变化时重新测量

```typescript
useEffect(() => {
    virtualizer.measure?.();
}, [messages, streamingContent]);
```

### 涉及文件

- `webview/src/features/chat/components/MessageList.tsx`

---

## 第三章：调试支持恢复

### 现状问题

`vite.config.ts` 中 `drop_console: true` 移除所有日志，JCEF 环境无法调试。

### 修复方案

#### 3.1 条件化日志配置

```typescript
export default defineConfig(({ mode }) => ({
    build: {
        minify: 'terser',
        terserOptions: mode === 'production' ? {
            compress: {
                drop_console: false, // 保留日志
                pure_funcs: ['console.log'] // 仅移除开发日志
            }
        } : undefined,
    }
}));
```

#### 3.2 添加 JCEF 专用日志工具

```typescript
// webview/src/shared/utils/logger.ts
export const logger = {
    debug: (...args: any[]) => {
        if (window.ccBackend?.isDevMode) {
            console.log('[cc-gui debug]', ...args);
        }
    },
    error: (...args: any[]) => {
        console.error('[cc-gui error]', ...args);
        window.ccBackend?.send('logError', { message: args.join(' ') });
    },
    bridge: (action: string, params?: any) => {
        if (window.ccBackend?.isDevMode) {
            console.log('[cc-gui bridge]', action, params);
        }
    }
};
```

#### 3.3 添加 Bridge 通信追踪

```typescript
// webview/src/lib/java-bridge.ts
private pendingRequests = new Map<string, { timestamp: number, action: string }>();

invoke<T>(action: string, params?: any): Promise<T> {
    const queryId = nanoid();
    const startTime = Date.now();

    this.pendingRequests.set(queryId, { timestamp: startTime, action });
    logger.bridge(action, params);

    // 超时检测
    setTimeout(() => {
        if (this.pendingRequests.has(queryId)) {
            logger.error(`Request timeout: ${action} (${Date.now() - startTime}ms)`);
        }
    }, 10000);

    return new Promise((resolve, reject) => {
        // ... 请求逻辑
    });
}
```

### 涉及文件

- `webview/vite.config.ts`
- `webview/src/shared/utils/logger.ts` (新建)
- `webview/src/lib/java-bridge.ts`

---

## 第四章：JSON 注入风险修复

### 现状问题

`CefBrowserPanel.kt` 中手动转义 JSON 可能因特殊字符导致 JS 执行失败。

### 修复方案：使用 JBCefJSQuery

#### 4.1 创建 JSQuery 实例

```kotlin
// CefBrowserPanel.kt
private val jsQuery: JBCefJSQuery by lazy {
    JBCefJSQuery.create(this).apply {
        addHandler { msg ->
            handleJavaScriptMessage(msg)
            JBCefJSQuery.Response("ok")
        }
    }
}
```

#### 4.2 安全发送 JSON

```kotlin
fun sendToJavaScript(event: String, data: Map<String, Any>) {
    val jsonData = JsonUtils.toJson(data)

    // JBCefJSQuery.inject 自动处理转义
    val js = """
        if (window.ccEvents) {
            window.ccEvents.emit('$event', $jsonData);
        }
    """.trimIndent()

    browser?.getCefBrowser()?.executeJavaScript(
        js,
        browser?.getCefBrowser()?.getURL(),
        0
    )
}
```

#### 4.3 添加 JS 端错误捕获

```typescript
// webview/src/main/index.tsx
window.ccEvents = {
    emit: (event: string, data: any) => {
        try {
            eventBus.emit(event, data);
        } catch (error) {
            console.error('[ccEvents] Emit error:', { event, data, error });
            window.ccBackend?.send('jsError', { event, error: String(error) });
        }
    }
};
```

#### 4.4 单元测试

```kotlin
// src/test/kotlin/.../browser/JsonInjectionTest.kt
class JsonInjectionTest {
    @Test
    fun `should handle special characters`() {
        val specialData = mapOf(
            "text" to "Hello\nWorld'\"\\",
            "emoji" to "🔥🚀",
            "binary" to "\u0000\u0001"
        )
        assertDoesNotThrow {
            sendToJavaScript("test", specialData)
        }
    }
}
```

### 涉及文件

- `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt`
- `webview/src/main/index.tsx`
- `src/test/kotlin/.../browser/JsonInjectionTest.kt` (新建)

---

## 第五章：测试策略

### 测试范围

#### 5.1 单元测试

- `MessageList.test.tsx` - 虚拟滚动行为
- `java-bridge.test.ts` - Bridge 通信
- `JsonInjectionTest.kt` - JSON 特殊字符处理

#### 5.2 集成测试

- JCEF 环境下的端到端测试
- Bridge 注入时序测试
- 流式输出渲染测试

#### 5.3 手动测试场景

- 长消息（>10000 字符）渲染
- 特殊字符（emoji、换行、引号）处理
- 快速切换会话
- 网络中断恢复

---

## 第六章：实施计划

### 阶段划分

| 阶段 | 任务 | 预计时间 | 风险 |
|------|------|----------|------|
| 1 | 移除 CSS `contain: strict` + 启用虚拟滚动测量 | 0.5 天 | 低 |
| 2 | 简化 Bridge 架构（移除 iframe） | 1 天 | 中 |
| 3 | 实现 JBCefJSQuery JSON 处理 | 0.5 天 | 低 |
| 4 | 添加调试日志和健康检查 | 0.5 天 | 低 |
| 5 | 单元测试和集成测试 | 1 天 | 中 |
| 6 | JCEF 环境手动测试 | 0.5 天 | 中 |

**总计：** 约 3-4 天

### 风险缓解

- 每个阶段完成后提交到分支，便于回滚
- 保留现有 iframe 代码作为注释，便于恢复
- 添加 feature flag 可快速切换新旧 Bridge

#### Feature Flag 实现

```kotlin
// CefBrowserPanel.kt
companion object {
    private const val USE_NEW_BRIDGE = true // Feature flag
}

private fun injectBackendJavaScript() {
    if (USE_NEW_BRIDGE) {
        injectNewBridge() // 直接 JBCefJSQuery
    } else {
        injectOldBridge() // 保留 iframe 方案
    }
}
```

---

## 附录：参考实现

### jetbrains-cc-gui 的 Bridge 实现

```typescript
// jetbrains-cc-gui/webview/src/utils/bridge.ts
const callBridge = (payload: string) => {
    if (window.sendToJava) {
        window.sendToJava(payload);
        return true;
    }
    return false;
};

export const sendBridgeEvent = (event: string, content = '') => {
    return callBridge(`${event}:${content}`);
};
```

关键特点：
- 简单的 `event:content` 字符串格式
- 直接通过 `window.sendToJava` 通信
- 无 iframe 中转层

---

## 验收标准

1. 消息列表正常渲染，无重叠或空白
2. 虚拟滚动流畅，长消息正常显示
3. 特殊字符（emoji、换行、引号）正确处理
4. Bridge 通信稳定，无消息丢失
5. 生产环境可查看调试日志
6. 所有单元测试通过
