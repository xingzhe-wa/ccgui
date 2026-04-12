# cc-gui 沙箱环境渲染修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 修复 cc-gui 前端在 JCEF 沙箱环境中的渲染问题，包括消息列表显示异常、虚拟滚动失效、消息重叠等。

**架构:** 简化 Bridge 通信架构，移除 iframe 中转层，使用 JBCefJSQuery 直接通信；修复虚拟滚动配置；恢复调试日志支持。

**技术栈:** Kotlin 1.9, React 18, TypeScript, Vite 5, JCEF (JetBrains Chromium Embedded Framework), JUnit 5, Vitest

---

## 文件结构

### 修改的文件

| 文件 | 职责 |
|------|------|
| `webview/src/features/chat/components/MessageList.tsx` | 虚拟滚动消息列表组件 |
| `webview/vite.config.ts` | Vite 构建配置 |
| `webview/src/main/index.tsx` | React 应用入口，Pre-Registration 模式 |
| `webview/src/lib/java-bridge.ts` | Java Bridge 通信封装 |
| `webview/src/shared/utils/logger.ts` | 日志工具（新建） |
| `webview/src/main/App.tsx` | 根组件，消费预注册消息 |
| `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt` | JCEF 浏览器面板，Bridge 注入 |

### 新建的文件

| 文件 | 职责 |
|------|------|
| `webview/src/features/chat/components/MessageList.test.tsx` | MessageList 单元测试 |
| `webview/src/lib/java-bridge.test.ts` | JavaBridge 单元测试 |
| `src/test/kotlin/com/github/claudecode/ccgui/browser/JsonInjectionTest.kt` | JSON 注入测试 |

---

## Task 1: 修复虚拟滚动配置

**Files:**
- Modify: `webview/src/features/chat/components/MessageList.tsx`

### 步骤

- [ ] **Step 1: 移除 `contain: strict` CSS 属性**

打开 `webview/src/features/chat/components/MessageList.tsx`，找到包含 `contain: 'strict'` 的 div 元素并删除 style 属性。

```tsx
// 修改前（约第 139 行）
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

- [ ] **Step 2: 启用虚拟滚动动态测量**

找到 `useVirtualizer` 调用（约第 44-51 行），将 `measureElement` 从 `undefined` 改为实际函数。

```tsx
// 修改前
const virtualizer = useVirtualizer({
  count: messages.length,
  getScrollElement: () => parentRef.current,
  estimateSize: () => ESTIMATED_MESSAGE_HEIGHT,
  overscan: OVERSCAN,
  measureElement: undefined
});

// 修改后
const virtualizer = useVirtualizer({
  count: messages.length,
  getScrollElement: () => parentRef.current,
  estimateSize: () => ESTIMATED_MESSAGE_HEIGHT,
  overscan: OVERSCAN,
  measureElement: (element) => element?.getBoundingClientRect().height ?? ESTIMATED_MESSAGE_HEIGHT
});
```

- [ ] **Step 3: 添加内容变化时重新测量**

在组件中添加 useEffect，监听消息和流式内容变化。

```tsx
// 在组件内添加（约第 120 行附近，在现有 useEffect 之后）
useEffect(() => {
  virtualizer.measure?.();
}, [messages, streamingContent, virtualizer.measure]);
```

- [ ] **Step 4: 验证修改**

检查文件语法正确，确保没有 TypeScript 错误。

```bash
cd webview && npm run type-check
```

预期：无类型错误

- [ ] **Step 5: 提交更改**

```bash
git add webview/src/features/chat/components/MessageList.tsx
git commit -m "fix: remove contain:strict and enable virtual scrolling dynamic measurement

- Remove CSS contain:strict that causes rendering issues in JCEF
- Enable measureElement for dynamic height calculation
- Add re-measurement on content changes"
```

---

## Task 2: 创建日志工具模块

**Files:**
- Create: `webview/src/shared/utils/logger.ts`

### 步骤

- [ ] **Step 1: 创建日志工具文件**

创建 `webview/src/shared/utils/logger.ts`，实现 JCEF 环境专用日志工具。

```typescript
/**
 * JCEF 环境专用日志工具
 * 在生产环境保留关键日志，便于调试
 */

interface CcBackend {
  isDevMode?: boolean;
  send?: (action: string, params?: any) => void;
}

declare global {
  interface Window {
    ccBackend?: CcBackend;
  }
}

export const logger = {
  /**
   * 调试日志 - 仅在开发模式输出
   */
  debug: (...args: any[]) => {
    if (typeof window !== 'undefined' && window.ccBackend?.isDevMode) {
      console.log('[cc-gui debug]', ...args);
    }
  },

  /**
   * 错误日志 - 始终输出，并发送到后端
   */
  error: (...args: any[]) => {
    console.error('[cc-gui error]', ...args);
    if (typeof window !== 'undefined' && window.ccBackend?.send) {
      try {
        window.ccBackend.send('logError', { message: args.map(String).join(' ') });
      } catch {
        // 忽略日志发送失败
      }
    }
  },

  /**
   * Bridge 通信日志 - 仅在开发模式输出
   */
  bridge: (action: string, params?: any) => {
    if (typeof window !== 'undefined' && window.ccBackend?.isDevMode) {
      console.log(`[cc-gui bridge] ${action}`, params ?? '');
    }
  },

  /**
   * 信息日志 - 始终输出
   */
  info: (...args: any[]) => {
    console.info('[cc-gui info]', ...args);
  },

  /**
   * 警告日志 - 始终输出
   */
  warn: (...args: any[]) => {
    console.warn('[cc-gui warn]', ...args);
  }
};
```

- [ ] **Step 2: 验证文件创建**

确认文件已创建且无语法错误。

```bash
cd webview && npm run type-check
```

预期：无类型错误

- [ ] **Step 3: 提交更改**

```bash
git add webview/src/shared/utils/logger.ts
git commit -m "feat: add JCEF-specific logger utility

- Add logger with dev-mode aware debug output
- Send errors to backend via bridge
- Preserve critical logs in production"
```

---

## Task 3: 更新 Vite 构建配置

**Files:**
- Modify: `webview/vite.config.ts`

### 步骤

- [ ] **Step 1: 修改 terser 配置**

打开 `webview/vite.config.ts`，找到 terserOptions 配置（约第 87-91 行），修改日志保留策略。

```typescript
// 修改前
terserOptions: {
  compress: {
    drop_console: true,
    pure_funcs: ['console.log', 'console.info', 'console.debug', 'console.warn']
  }
}

// 修改后
terserOptions: {
  compress: {
    drop_console: false,
    pure_funcs: ['console.log'] // 仅移除开发时的 console.log，保留 error/warn/info
  }
}
```

- [ ] **Step 2: 验证配置**

```bash
cd webview && npm run build
```

预期：构建成功，生产代码中保留 console.error/console.warn/console.info

- [ ] **Step 3: 提交更改**

```bash
git add webview/vite.config.ts
git commit -m "build: preserve critical logs in production

- Keep console.error/warn/info for debugging in JCEF
- Only remove console.log from production build"
```

---

## Task 4: 实现 Pre-Registration 模式

**Files:**
- Modify: `webview/src/main/index.tsx`
- Modify: `webview/src/main/App.tsx`

### 步骤

- [ ] **Step 1: 在 index.tsx 中添加预注册逻辑**

打开 `webview/src/main/index.tsx`，在 ReactDOM.createRoot 调用之前添加预注册代码。

```tsx
// 在文件顶部添加（约第 10 行之后）
interface PendingMessage {
  type: string;
  data: any;
}

const pendingMessages: PendingMessage[] = [];

// 预注册所有可能的 Bridge 回调
if (typeof window !== 'undefined') {
  // updateMessages 回调
  if (!window.updateMessages) {
    window.updateMessages = (json: string, sequence?: string | number) => {
      pendingMessages.push({ type: 'updateMessages', data: { json, sequence } });
    };
  }

  // onStreamStart 回调
  if (!window.onStreamStart) {
    window.onStreamStart = () => {
      pendingMessages.push({ type: 'onStreamStart', data: null });
    };
  }

  // onContentDelta 回调
  if (!window.onContentDelta) {
    window.onContentDelta = (delta: string) => {
      pendingMessages.push({ type: 'onContentDelta', data: { delta } });
    };
  }

  // onStreamEnd 回调
  if (!window.onStreamEnd) {
    window.onStreamEnd = (sequence?: string | number) => {
      pendingMessages.push({ type: 'onStreamEnd', data: { sequence } });
    };
  }

  // showPermissionDialog 回调
  if (!window.showPermissionDialog) {
    window.showPermissionDialog = (json: string) => {
      pendingMessages.push({ type: 'showPermissionDialog', data: { json } });
    };
  }

  // 提供消费缓存的函数
  (window as any).__consumePendingMessages = () => {
    return pendingMessages.splice(0);
  };
}
```

- [ ] **Step 2: 在 App.tsx 中消费缓存消息**

打开 `webview/src/main/App.tsx`，在 ErrorBoundary 组件内部添加消费逻辑。

```tsx
// 在 ErrorBoundary 组件内部添加（约第 30 行，在状态声明之后）
const consumePendingMessages = useCallback(() => {
  if (typeof window === 'undefined') return;

  const consume = (window as any).__consumePendingMessages;
  if (!consume) return;

  const messages = consume() as Array<{ type: string; data: any }>;

  messages.forEach((msg) => {
    switch (msg.type) {
      case 'updateMessages':
        // 调用现有的消息更新处理
        if (window.ccEvents) {
          window.ccEvents.emit('response', { result: JSON.parse(msg.data.json) });
        }
        break;
      case 'onStreamStart':
        if (window.ccEvents) {
          window.ccEvents.emit('streamStart');
        }
        break;
      case 'onContentDelta':
        if (window.ccEvents) {
          window.ccEvents.emit('contentDelta', msg.data.delta);
        }
        break;
      case 'onStreamEnd':
        if (window.ccEvents) {
          window.ccEvents.emit('streamEnd', msg.data.sequence);
        }
        break;
      case 'showPermissionDialog':
        if (window.ccEvents) {
          window.ccEvents.emit('showPermission', JSON.parse(msg.data.json));
        }
        break;
    }
  });
}, []);

// 添加 useEffect 来消费缓存（约第 60 行，在现有 useEffect 之后）
useEffect(() => {
  // 延迟消费，确保事件监听器已注册
  const timer = setTimeout(consumePendingMessages, 100);
  return () => clearTimeout(timer);
}, [consumePendingMessages]);
```

- [ ] **Step 3: 验证修改**

```bash
cd webview && npm run type-check
```

预期：无类型错误

- [ ] **Step 4: 提交更改**

```bash
git add webview/src/main/index.tsx webview/src/main/App.tsx
git commit -m "feat: implement pre-registration pattern for Bridge callbacks

- Register window callbacks before React mount
- Cache early messages from Java backend
- Consume pending messages after React hydration"
```

---

## Task 5: 添加 Bridge 通信追踪

**Files:**
- Modify: `webview/src/lib/java-bridge.ts`

### 步骤

- [ ] **Step 1: 在 java-bridge.ts 中添加日志和追踪**

打开 `webview/src/lib/java-bridge.ts`，添加请求追踪和超时检测。

```typescript
// 在文件顶部添加导入（约第 5 行）
import { logger } from '../shared/utils/logger';

// 在 JavaBridge 类内部添加私有字段（约第 20 行，在现有字段之后）
private pendingRequests = new Map<string, { timestamp: number; action: string; timer: NodeJS.Timeout }>();
private readonly REQUEST_TIMEOUT = 10000; // 10秒超时

// 修改 invoke 方法（约第 40-60 行），添加追踪逻辑
public invoke<T>(action: string, params?: any): Promise<T> {
  const queryId = nanoid();
  const startTime = Date.now();

  logger.bridge(action, params);

  return new Promise<T>((resolve, reject) => {
    // 记录请求
    this.pendingRequests.set(queryId, {
      timestamp: startTime,
      action,
      timer: setTimeout(() => {
        if (this.pendingRequests.has(queryId)) {
          logger.error(`Bridge request timeout: ${action} (${Date.now() - startTime}ms)`);
          this.pendingRequests.delete(queryId);
          reject(new Error(`Bridge request timeout: ${action}`));
        }
      }, this.REQUEST_TIMEOUT)
    });

    // 发送请求
    this.sendRequest(queryId, action, params);

    // 注册响应处理
    this.registerResponseHandler(queryId, (result: T, error?: string) => {
      const pending = this.pendingRequests.get(queryId);
      if (pending) {
        clearTimeout(pending.timer);
        this.pendingRequests.delete(queryId);
        const duration = Date.now() - pending.timestamp;
        logger.debug(`Bridge response: ${action} (${duration}ms)`);
      }

      if (error) {
        logger.error(`Bridge error: ${action} - ${error}`);
        reject(new Error(error));
      } else {
        resolve(result);
      }
    });
  });
}

// 添加清理方法（在类末尾）
public cleanup(): void {
  this.pendingRequests.forEach(({ timer }) => clearTimeout(timer));
  this.pendingRequests.clear();
}
```

- [ ] **Step 2: 在组件卸载时清理**

打开 `webview/src/main/components/JcefBrowser.tsx`，添加清理逻辑。

```tsx
// 在组件卸载时清理（约第 50 行，在 useEffect 的 cleanup 函数中）
useEffect(() => {
  // ... 现有代码

  return () => {
    // 清理 pending 请求
    if (typeof window !== 'undefined' && (window as any).__javaBridgeCleanup) {
      (window as any).__javaBridgeCleanup();
    }
  };
}, [/* 现有依赖 */]);
```

- [ ] **Step 3: 验证修改**

```bash
cd webview && npm run type-check
```

预期：无类型错误

- [ ] **Step 4: 提交更改**

```bash
git add webview/src/lib/java-bridge.ts webview/src/main/components/JcefBrowser.tsx
git commit -m "feat: add Bridge communication tracking and timeout detection

- Log all Bridge requests in dev mode
- Add 10-second timeout for pending requests
- Cleanup pending requests on component unmount"
```

---

## Task 6: 简化 Bridge 架构（移除 iframe）

**Files:**
- Modify: `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt`

### 步骤

- [ ] **Step 1: 添加 JBCefJSQuery 实例**

打开 `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt`，在类属性区域添加 jsQuery。

```kotlin
// 在类属性区域添加（约第 80 行，在 browser 属性之后）
private val jsQuery: JBCefJSQuery by lazy {
    JBCefJSQuery.create(this).apply {
        addHandler { msg ->
            log.debug("Received message from JavaScript: {}", msg)
            handleJavaScriptMessage(msg)
            JBCefJSQuery.Response("ok")
        }
    }
}
```

- [ ] **Step 2: 添加 Feature Flag**

在 companion object 中添加 feature flag。

```kotlin
// 在 companion object 中添加（约第 50 行）
companion object {
    private const val USE_NEW_BRIDGE = true // Feature flag: true = 直接 JBCefJSQuery, false = iframe 方案

    // ... 现有常量
}
```

- [ ] **Step 3: 添加注入同步锁**

在类属性区域添加注入状态标志。

```kotlin
// 在类属性区域添加（约第 80 行）
@Volatile
private var isBridgeInjected = false

private val bridgeInjectionLock = Any()
```

- [ ] **Step 4: 重写 injectBackendJavaScript 方法**

找到 `injectBackendJavaScript` 方法（约第 285-350 行），添加新的注入逻辑。

```kotlin
// 替换现有方法
private fun injectBackendJavaScript() {
    // 防止重复注入
    synchronized(bridgeInjectionLock) {
        if (isBridgeInjected) {
            log.debug("Bridge already injected, skipping")
            return
        }
        isBridgeInjected = true
    }

    val cefBrowser = browser?.getCefBrowser() ?: run {
        log.warn("Browser is null, cannot inject Bridge")
        return
    }

    if (USE_NEW_BRIDGE) {
        injectNewBridge(cefBrowser)
    } else {
        injectOldBridge(cefBrowser)
    }
}

// 新增方法：直接 JBCefJSQuery 方式
private fun injectNewBridge(cefBrowser: CefBrowser) {
    // 获取 JBCefJSQuery 注入的函数
    val injectFunction = jsQuery.inject("msg")

    val jsCode = """
        (function() {
            if (window.ccBackend) {
                console.log('[Bridge] ccBackend already exists, skipping injection');
                return;
            }

            console.log('[Bridge] Injecting new ccBackend...');

            var _jcefQuery = $injectFunction;

            window.ccBackend = {
                send: function(action, params) {
                    var queryId = 'q_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                    var payload = JSON.stringify({
                        queryId: queryId,
                        action: action,
                        params: params || {}
                    });

                    console.log('[Bridge] Sending:', action, params);

                    try {
                        _jcefQuery(payload);
                    } catch (e) {
                        console.error('[Bridge] Send error:', e);
                    }

                    return new Promise(function(resolve, reject) {
                        window.ccBackend._pendingRequests[queryId] = { resolve: resolve, reject: reject };
                        // 10秒超时
                        setTimeout(function() {
                            if (window.ccBackend._pendingRequests[queryId]) {
                                delete window.ccBackend._pendingRequests[queryId];
                                reject(new Error('Bridge request timeout: ' + action));
                            }
                        }, 10000);
                    });
                },
                _pendingRequests: {},
                _healthCheck: function(timestamp) {
                    console.log('[Bridge] Health check:', timestamp);
                },
                isDevMode: true
            };

            // 响应处理函数（由 Java 端调用）
            window.__jcef_response__ = function(queryId, result, error) {
                console.log('[Bridge] Response:', queryId, error ? 'ERROR' : 'OK');
                var pending = window.ccBackend._pendingRequests[queryId];
                if (pending) {
                    delete window.ccBackend._pendingRequests[queryId];
                    if (error) {
                        pending.reject(new Error(error));
                    } else {
                        pending.resolve(result);
                    }
                }
            };

            console.log('[Bridge] ccBackend injected successfully');
        })();
    """.trimIndent()

    cefBrowser.executeJavaScript(jsCode, cefBrowser.url, 0)
    log.info("New Bridge injected successfully")
}

// 保留旧方法作为回退方案
private fun injectOldBridge(cefBrowser: CefBrowser) {
    // 现有的 iframe 注入逻辑保持不变
    // ... (保持现有代码)
}
```

- [ ] **Step 5: 更新 sendToJavaScript 方法**

找到 `sendToJavaScript` 方法（约第 520-535 行），使用 JBCefJSQuery 方式。

```kotlin
// 替换现有方法
fun sendToJavaScript(event: String, data: Map<String, Any>) {
    browser?.let { b ->
        val cefBrowser = b.getCefBrowser()

        if (USE_NEW_BRIDGE) {
            // 新方式：直接传递 JSON，JBCefJSQuery 自动处理转义
            val jsonData = JsonUtils.toJson(data)
            val jsCode = """
                if (window.ccEvents) {
                    try {
                        window.ccEvents.emit('$event', $jsonData);
                    } catch (e) {
                        console.error('[ccEvents] Emit error:', e);
                        if (window.ccBackend && window.ccBackend.send) {
                            window.ccBackend.send('jsError', { event: '$event', error: String(e) });
                        }
                    }
                }
            """.trimIndent()
            cefBrowser.executeJavaScript(jsCode, cefBrowser.url, 0)
        } else {
            // 旧方式：手动转义（保留作为回退）
            val jsonData = JsonUtils.toJson(data)
            val safeEvent = event.replace("\\", "\\\\").replace("'", "\\'")
            val safeJsonForJs = jsonData
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            val js = "window.ccEvents && window.ccEvents.emit('$safeEvent', JSON.parse('$safeJsonForJs'));"
            cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        }
    }
}
```

- [ ] **Step 6: 添加响应处理**

在 `handleJavaScriptMessage` 方法中添加响应处理逻辑。

```kotlin
// 在 handleJavaScriptMessage 方法中添加（约第 350 行）
private fun handleJavaScriptMessage(msg: String) {
    try {
        val json = JsonParser.parseString(msg).asJsonObject
        val queryId = json.get("queryId")?.asString
        val action = json.get("action")?.asString
        val params = json.get("params")?.asJsonObject

        log.debug("Handling action: {} with queryId: {}", action, queryId)

        when (action) {
            "logError" -> {
                val message = params?.get("message")?.asString
                log.error("Frontend error: {}", message)
                // 发送响应
                sendResponse(queryId, null, null)
            }
            else -> {
                // 处理其他 action
                jsRequestHandler?.handleRequest(action, params)?.thenAccept { result ->
                    sendResponse(queryId, result, null)
                }?.exceptionally { e ->
                    sendResponse(queryId, null, e.message)
                    null
                }
            }
        }
    } catch (e: Exception) {
        log.error("Error handling JavaScript message", e)
    }
}

// 新增方法：发送响应
private fun sendResponse(queryId: String?, result: String?, error: String?) {
    if (queryId == null) return

    val jsCode = """
        if (window.__jcef_response__) {
            window.__jcef_response__('$queryId', ${result ?: "null"}, ${error?.let { "'$it'" } ?: "null"});
        }
    """.trimIndent()

    browser?.getCefBrowser()?.executeJavaScript(jsCode, browser?.getCefBrowser()?.url, 0)
}
```

- [ ] **Step 7: 验证编译**

```bash
cd . && ./gradlew compileKotlin
```

预期：编译成功

- [ ] **Step 8: 提交更改**

```bash
git add src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt
git commit -m "feat: simplify Bridge architecture using direct JBCefJSQuery

- Remove iframe middle layer, use direct JBCefJSQuery communication
- Add feature flag to switch between old and new Bridge
- Implement Pre-Registration pattern support
- Add response handling for pending requests
- Use JBCefJSQuery.inject for safe JSON handling"
```

---

## Task 7: 添加 Bridge 健康检查

**Files:**
- Modify: `src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt`

### 步骤

- [ ] **Step 1: 添加健康检查方法**

在 `CefBrowserPanel.kt` 中添加健康检查定时任务。

```kotlin
// 在类中添加新方法（约第 600 行，在 loadHtmlPage 方法之后）
private fun startBridgeHealthCheck() {
    scope.launch {
        while (isActive && USE_NEW_BRIDGE) {
            delay(5000) // 5秒心跳
            try {
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
            } catch (e: Exception) {
                log.warn("Bridge health check failed", e)
            }
        }
    }
}
```

- [ ] **Step 2: 在 Bridge 注入后启动健康检查**

修改 `injectBackendJavaScript` 方法，在注入成功后启动健康检查。

```kotlin
// 在 injectNewBridge 方法末尾添加（约第 350 行）
cefBrowser.executeJavaScript(jsCode, cefBrowser.url, 0)
log.info("New Bridge injected successfully")

// 启动健康检查
startBridgeHealthCheck()
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew compileKotlin
```

预期：编译成功

- [ ] **Step 4: 提交更改**

```bash
git add src/main/kotlin/com/github/claudecode/ccgui/browser/CefBrowserPanel.kt
git commit -m "feat: add Bridge health check mechanism

- Send 5-second heartbeat to frontend
- Log Bridge health status
- Detect Bridge unavailability"
```

---

## Task 8: 编写 MessageList 单元测试

**Files:**
- Create: `webview/src/features/chat/components/MessageList.test.tsx`

### 步骤

- [ ] **Step 1: 创建测试文件**

创建 `webview/src/features/chat/components/MessageList.test.tsx`。

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import MessageList from './MessageList';

// Mock the virtualizer
vi.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: vi.fn(() => ({
    getVirtualItems: () => [],
    getTotalSize: () => 0,
    measureElement: (element: Element) => element?.getBoundingClientRect().height ?? 120,
  })),
}));

describe('MessageList', () => {
  const mockMessages = [
    {
      id: '1',
      role: 'user' as const,
      content: 'Hello',
    },
    {
      id: '2',
      role: 'assistant' as const,
      content: 'Hi there!',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render messages without contain:strict', () => {
    const { container } = render(<MessageList messages={mockMessages} />);
    const scrollContainer = container.querySelector('.overflow-y-auto');

    expect(scrollContainer).toBeInTheDocument();
    // 验证没有 contain:strict
    expect(scrollContainer?.getAttribute('style')).not.toContain('contain');
  });

  it('should call measureElement on mount', () => {
    const { useVirtualizer } = require('@tanstack/react-virtual');
    const mockMeasure = vi.fn();

    useVirtualizer.mockImplementation(() => ({
      getVirtualItems: () => [],
      getTotalSize: () => 0,
      measureElement: mockMeasure,
    }));

    render(<MessageList messages={mockMessages} />);

    expect(mockMeasure).toHaveBeenCalled();
  });

  it('should update measurement when messages change', () => {
    const { useVirtualizer } = require('@tanstack/react-virtual');
    const mockMeasure = vi.fn();

    useVirtualizer.mockImplementation(() => ({
      getVirtualItems: () => [],
      getTotalSize: () => 0,
      measure: mockMeasure,
    }));

    const { rerender } = render(<MessageList messages={mockMessages} />);

    rerender(<MessageList messages={[...mockMessages, { id: '3', role: 'user', content: 'New' }]} />);

    expect(mockMeasure).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 运行测试**

```bash
cd webview && npm test -- MessageList.test.tsx
```

预期：测试通过

- [ ] **Step 3: 提交更改**

```bash
git add webview/src/features/chat/components/MessageList.test.tsx
git commit -m "test: add MessageList unit tests

- Test removal of contain:strict CSS
- Test dynamic measurement behavior
- Test re-measurement on content changes"
```

---

## Task 9: 编写 JavaBridge 单元测试

**Files:**
- Create: `webview/src/lib/java-bridge.test.ts`

### 步骤

- [ ] **Step 1: 创建测试文件**

创建 `webview/src/lib/java-bridge.test.ts`。

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { JavaBridge } from './java-bridge';

// Mock logger
vi.mock('../shared/utils/logger', () => ({
  logger: {
    debug: vi.fn(),
    error: vi.fn(),
    bridge: vi.fn(),
  },
}));

describe('JavaBridge', () => {
  let bridge: JavaBridge;

  beforeEach(() => {
    // Mock window.ccBackend
    (global as any).window = {
      ccBackend: {
        send: vi.fn((action: string, params: any) => {
          // 模拟响应
          setTimeout(() => {
            const queryId = JSON.parse(params).queryId;
            if ((global as any).window.__jcef_response__) {
              (global as any).window.__jcef_response__(queryId, '{"result":"ok"}', null);
            }
          }, 10);
        }),
      },
    };
    bridge = new JavaBridge();
  });

  afterEach(() => {
    bridge.cleanup();
  });

  it('should send request and receive response', async () => {
    const result = await bridge.invoke<{ result: string }>('testAction', { foo: 'bar' });

    expect(result).toEqual({ result: 'ok' });
  });

  it('should timeout after 10 seconds', async () => {
    // Mock a never-responding request
    (global as any).window.ccBackend.send = vi.fn();

    await expect(
      bridge.invoke('slowAction')
    ).rejects.toThrow('Bridge request timeout');
  });

  it('should cleanup pending requests', () => {
    const pendingCount = (bridge as any).pendingRequests.size;
    expect(pendingCount).toBe(0);

    bridge.cleanup();

    const afterCleanup = (bridge as any).pendingRequests.size;
    expect(afterCleanup).toBe(0);
  });
});
```

- [ ] **Step 2: 运行测试**

```bash
cd webview && npm test -- java-bridge.test.ts
```

预期：测试通过

- [ ] **Step 3: 提交更改**

```bash
git add webview/src/lib/java-bridge.test.ts
git commit -m "test: add JavaBridge unit tests

- Test request/response cycle
- Test timeout detection
- Test cleanup of pending requests"
```

---

## Task 10: 编写 JSON 注入测试

**Files:**
- Create: `src/test/kotlin/com/github/claudecode/ccgui/browser/JsonInjectionTest.kt`

### 步骤

- [ ] **Step 1: 创建测试目录**

```bash
mkdir -p src/test/kotlin/com/github/claudecode/ccgui/browser
```

- [ ] **Step 2: 创建测试文件**

创建 `src/test/kotlin/com/github/claudecode/ccgui/browser/JsonInjectionTest.kt`。

```kotlin
package com.github.claudecode.ccgui.browser

import com.github.claudecode.ccgui.util.JsonUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonInjectionTest {

    @Test
    fun `should handle special characters in JSON`() {
        val specialData = mapOf(
            "text" to "Hello\nWorld'\"\\",
            "emoji" to "🔥🚀",
            "binary" to "\u0000\u0001",
            "quotes" to """{"key": "value"}""",
            "mixed" to "Line1\nLine2\tTabbed"
        )

        val json = JsonUtils.toJson(specialData)

        // 验证 JSON 可以正确解析
        val parsed = JsonUtils.fromJson(json, Map::class.java)
        assertEquals(specialData["text"], parsed["text"])
        assertEquals(specialData["emoji"], parsed["emoji"])
    }

    @Test
    fun `should handle empty strings and null values`() {
        val data = mapOf(
            "empty" to "",
            "nullValue" to null,
            "normal" to "value"
        )

        val json = JsonUtils.toJson(data)
        val parsed = JsonUtils.fromJson(json, Map::class.java)

        assertEquals("", parsed["empty"])
        assertEquals(null, parsed["nullValue"])
        assertEquals("value", parsed["normal"])
    }

    @Test
    fun `should handle large JSON payloads`() {
        val largeText = "A".repeat(10000)
        val data = mapOf("large" to largeText)

        val json = JsonUtils.toJson(data)
        val parsed = JsonUtils.fromJson(json, Map::class.java)

        assertEquals(largeText, parsed["large"])
    }

    @Test
    fun `should handle nested objects`() {
        val nested = mapOf(
            "level1" to mapOf(
                "level2" to mapOf(
                    "level3" to "deep value"
                )
            )
        )

        val json = JsonUtils.toJson(nested)
        val parsed = JsonUtils.fromJson(json, Map::class.java)

        val level1 = parsed["level1"] as Map<*, *>
        val level2 = level1["level2"] as Map<*, *>
        assertEquals("deep value", level2["level3"])
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
./gradlew test --tests JsonInjectionTest
```

预期：测试通过

- [ ] **Step 4: 提交更改**

```bash
git add src/test/kotlin/com/github/claudecode/ccgui/browser/JsonInjectionTest.kt
git add src/test/kotlin/com/github/claudecode/ccgui/browser/
git commit -m "test: add JSON injection safety tests

- Test special character handling
- Test empty and null values
- Test large payload handling
- Test nested object serialization"
```

---

## Task 11: 手动测试验证

**Files:**
- None (manual testing)

### 步骤

- [ ] **Step 1: 构建前端**

```bash
cd webview && npm run build
```

预期：构建成功，生成 dist 目录

- [ ] **Step 2: 构建后端**

```bash
./gradlew buildPlugin
```

预期：构建成功，生成 plugin JAR

- [ ] **Step 3: 在 IntelliJ IDEA 中安装测试**

1. 打开 IntelliJ IDEA
2. 进入 Settings → Plugins
3. 点击齿轮图标 → Install Plugin from Disk
4. 选择构建的 JAR 文件
5. 重启 IDE

- [ ] **Step 4: 验证基本渲染**

1. 打开 Claude Code 工具窗口
2. 发送简单消息 "hello"
3. 验证消息正确显示，无重叠或空白

- [ ] **Step 5: 验证长消息渲染**

1. 发送包含长文本的消息（>5000 字符）
2. 验证虚拟滚动正常工作
3. 验证消息完整显示，无截断

- [ ] **Step 6: 验证特殊字符**

1. 发送包含 emoji 的消息：`测试 🔥🚀 💯`
2. 发送包含换行的消息：
```
第一行
第二行
第三行
```
3. 验证特殊字符正确显示

- [ ] **Step 7: 验证流式输出**

1. 发送会触发流式输出的请求（如 "写一个冒泡排序"）
2. 验证流式内容平滑显示
3. 验证完成后消息完整

- [ ] **Step 8: 验证会话切换**

1. 创建新会话
2. 在会话间快速切换
3. 验证每个会话的消息正确显示

- [ ] **Step 9: 验证调试日志**

1. 打开 IDE 的 Debug Log (Help → Show Log in Explorer)
2. 执行一些操作
3. 验证日志中包含 `[cc-gui]` 相关输出

- [ ] **Step 10: 记录测试结果**

创建测试报告文档。

```bash
cat > E:/work-File/code/ccgui/docs/superpowers/testing/2026-04-12-sandbox-rendering-manual-test.md << 'EOF'
# cc-gui 沙箱环境渲染修复 - 手动测试报告

**日期:** 2026-04-12
**测试人员:**
**IDE 版本:**

## 测试环境

- IntelliJ IDEA 版本:
- cc-gui 版本:
- 操作系统:

## 测试结果

| 测试项 | 结果 | 备注 |
|--------|------|------|
| 基本消息渲染 | ☐ 通过 / ☐ 失败 | |
| 长消息渲染 | ☐ 通过 / ☐ 失败 | |
| 特殊字符显示 | ☐ 通过 / ☐ 失败 | |
| 流式输出 | ☐ 通过 / ☐ 失败 | |
| 会话切换 | ☐ 通过 / ☐ 失败 | |
| 调试日志 | ☐ 通过 / ☐ 失败 | |

## 问题描述

记录测试中发现的任何问题：

## 截图

附上关键功能的截图：
EOF
```

- [ ] **Step 11: 提交测试报告**

```bash
mkdir -p docs/superpowers/testing
git add docs/superpowers/testing/2026-04-12-sandbox-rendering-manual-test.md
git commit -m "test: add manual test report for sandbox rendering fix"
```

---

## Task 12: 更新文档

**Files:**
- Create: `docs/superpowers/iteration-summaries/2026-04-12-sandbox-rendering-fix.md`

### 步骤

- [ ] **Step 1: 创建迭代总结文档**

```bash
mkdir -p docs/superpowers/iteration-summaries
```

- [ ] **Step 2: 编写总结**

创建 `docs/superpowers/iteration-summaries/2026-04-12-sandbox-rendering-fix.md`。

```markdown
# cc-gui 沙箱环境渲染修复 - 迭代总结

**日期:** 2026-04-12
**目标:** 修复 JCEF 沙箱环境中的渲染问题

## 完成的工作

### 1. 虚拟滚动修复
- 移除 CSS `contain: strict` 属性
- 启用 `measureElement` 动态测量
- 添加内容变化时重新测量

### 2. Bridge 架构简化
- 移除 iframe 中转层
- 使用 JBCefJSQuery 直接通信
- 实现 Pre-Registration 模式

### 3. 调试支持
- 恢复生产环境关键日志
- 添加 JCEF 专用日志工具
- 实现 Bridge 通信追踪

### 4. 安全性改进
- 使用 JBCefJSQuery 处理 JSON 转义
- 添加 JS 端错误捕获

## 技术决策

### 为什么移除 iframe？
- iframe 增加了不必要的复杂度
- postMessage 通信存在时序问题
- JBCefJSQuery 提供了更直接的通信方式

### 为什么保留 Feature Flag？
- 便于快速回滚到旧方案
- 允许在两种方案之间进行性能对比
- 降低部署风险

## 已知问题

- [列出任何已知问题或限制]

## 未来改进

- [ ] 考虑使用 vite-plugin-singlefile 单文件打包
- [ ] 实现双路径 Markdown 渲染（流式轻量 + 完整）
- [ ] 添加更多单元测试覆盖

## 参考资料

- 设计文档: `docs/superpowers/specs/2026-04-12-ccgui-sandbox-rendering-fix-design.md`
- 实施计划: `docs/superpowers/plans/2026-04-12-ccgui-sandbox-rendering-fix.md`
```

- [ ] **Step 3: 提交文档**

```bash
git add docs/superpowers/iteration-summaries/2026-04-12-sandbox-rendering-fix.md
git commit -m "docs: add iteration summary for sandbox rendering fix"
```

---

## 验收标准

完成所有任务后，应满足以下标准：

1. ✅ 消息列表正常渲染，无重叠或空白
2. ✅ 虚拟滚动流畅，长消息正常显示
3. ✅ 特殊字符（emoji、换行、引号）正确处理
4. ✅ Bridge 通信稳定，无消息丢失
5. ✅ 生产环境可查看调试日志
6. ✅ 所有单元测试通过
7. ✅ JCEF 环境手动测试通过

---

## 参考资料

- 设计文档: `docs/superpowers/specs/2026-04-12-ccgui-sandbox-rendering-fix-design.md`
- jetbrains-cc-gui 参考实现: `../jetbrains-cc-gui`
