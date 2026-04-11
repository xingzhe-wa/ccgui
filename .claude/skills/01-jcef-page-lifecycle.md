# JCEF 页面生命周期管理

## 核心规则

1. **`loadURL()` / `loadHTML()` 是异步操作** -- 调用后页面不会立即加载完成，DOM 不可用
2. **JavaScript 注入必须在页面加载完成后执行** -- 在 DOM 未就绪时执行 JS 会静默失败
3. **`JBCefJSQuery` 注入的函数 `__jcef_query_<id>__` 只在页面导航后可用** -- 页面刷新/重载后需要重新连接

## 页面加载流程

```
[1] JBCefBrowser 创建
         │
         ▼
[2] JBCefJSQuery.create() 注册回调
    setupJsQuery() -- 创建 jsQueryInvoker
         │
         ▼
[3] setupLoadListener() -- 通过反射注册 CefLoadHandler
    监听 onLoadingStateChange(isLoading=false) 事件
         │
         ▼
[4] loadURL() 或 loadHTML() 发起加载（异步）
    isPageLoaded = false
         │
         ▼
[5] 等待 onLoadEnd / onLoadingStateChange
    ├─ 正常路径：isLoading=false 触发 → isPageLoaded=true → loadListeners 依次执行
    └─ 超时路径：3 秒后仍未完成 → 强制 isPageLoaded=true → 日志告警
         │
         ▼
[6] injectBackendJavaScript() 注入 Bridge 脚本
    ├─ Step 1: 创建隐藏 iframe（data URL，用于 JS→Java 通信通道）
    ├─ Step 2: 注入 ccBackend + ccEvents 全局对象
    └─ Step 3: 设置 javaRequestCallback（轮询查找 __jcef_query__* 并连接）
         │
         ▼
[7] 前端 React 初始化完成
    java-bridge.ts init() 轮询 window.ccBackend && window.ccEvents
    监听 'response' 事件，完成 Bridge 就绪
```

## 反模式

```kotlin
// ❌ 错误：立即注入（页面可能还未加载）
browser.loadURL(url)
injectBackendJavaScript()  // DOM 不可用，JS 静默失败

// ❌ 错误：使用 EventQueue.invokeLater
browser.loadURL(url)
EventQueue.invokeLater { injectBackendJavaScript() }
// EDT 调度不等页面加载，仍可能在 DOM 就绪前执行

// ❌ 错误：使用固定延迟
browser.loadURL(url)
Thread.sleep(2000)
injectBackendJavaScript()
// 硬编码延迟不可靠，开发服务器快、生产 JAR 解压慢
```

## 正确模式

```kotlin
// ✅ 正确：使用 LoadHandler + 后备超时
isPageLoaded = false
browser?.loadURL(url)

// 路径 A：等待 onLoadEnd 触发
executeWhenPageLoaded(Runnable { injectBackendJavaScript() })

// 路径 B：后备超时（3 秒）
scope.launch {
    delay(3000)
    if (!isPageLoaded) {
        log.warn("Page load timeout, forcing bridge injection")
        isPageLoaded = true
        injectBackendJavaScript()
    }
}
```

`loadHtmlContent()` 和 `loadHtmlPage()` 内部已封装此模式，直接调用即可。

## 生产环境页面加载

`MyToolWindowFactory.loadProductionFrontend()` 使用 JAR 解压策略：

```
1. 通过 PluginClassLoader 获取 jar:file:...!/webview/dist/index.html
2. 解析 JAR 路径，使用 JarFile 提取 webview/ 目录到临时目录
3. 使用 file:// URL 加载 index.html
```

注意事项：
- 必须使用 `this::class.java.classLoader`（PluginClassLoader），而非系统类加载器
- 临时目录使用 `FileUtil.createTempDirectory()`，随插件生命周期管理
- 开发环境优先检测 `localhost:3000`，超时 1 秒自动回退

## 页面刷新 / 重载

页面刷新后需要重新注入 Bridge，当前实现中 `isPageLoaded` 会在 `onLoadingStateChange` 中重新设为 `true`，但 `loadListeners` 已清空。如果需要手动重载：

```kotlin
// 重置状态并重新加载
isPageLoaded = false
browser?.loadURL(url)
executeWhenPageLoaded(Runnable { injectBackendJavaScript() })
```

## 调试检查清单

- [ ] 是否在 `loadURL` 之后等待 `onLoadingStateChange(isLoading=false)`？
- [ ] `injectBackendJavaScript()` 是否在 DOM 就绪后执行？
- [ ] 是否有 3 秒后备超时机制？
- [ ] 页面刷新 / 重载后 Bridge 是否重新注入？
- [ ] `javaRequestCallback` 轮询 `__jcef_query__*` 是否有最大重试次数（当前 30 次 x 100ms = 3 秒）？
- [ ] 隐藏 iframe 是否设置了 `sandbox="allow-scripts"`？
- [ ] 生产环境是否使用 `PluginClassLoader` 而非系统类加载器？

## 关键代码位置

| 文件 | 方法 / 区域 | 职责 |
|------|------------|------|
| `CefBrowserPanel.kt` | `init()` | 创建 JBCefBrowser，调用 setupJsQuery + setupLoadListener |
| `CefBrowserPanel.kt` | `setupLoadListener()` | 通过反射注册 CefLoadHandler，监听加载完成 |
| `CefBrowserPanel.kt` | `executeWhenPageLoaded()` | 注册 / 立即执行页面加载完成回调 |
| `CefBrowserPanel.kt` | `loadHtmlPage()` / `loadHtmlContent()` | 封装加载 + 等待 + 超时后备 |
| `CefBrowserPanel.kt` | `injectBackendJavaScript()` | 三步注入 Bridge（iframe + ccBackend/ccEvents + javaRequestCallback） |
| `MyToolWindowFactory.kt` | `loadFrontendPage()` | 开发 / 生产环境路由 |
| `MyToolWindowFactory.kt` | `loadProductionFrontend()` | JAR 解压 + file:// 加载 |
