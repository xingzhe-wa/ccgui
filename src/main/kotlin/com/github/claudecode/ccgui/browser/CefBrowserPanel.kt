package com.github.claudecode.ccgui.browser

import com.github.claudecode.ccgui.browser.handler.JsRequestHandler
import com.github.claudecode.ccgui.bridge.ConnectionState
import com.github.claudecode.ccgui.util.JsonUtils
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JCEF 浏览器面板封装
 *
 * 核心职责：
 * - JCEF 浏览器实例管理
 * - JavaScript 注入和通信
 * - 页面加载管理
 * - 生命周期管理
 *
 * 请求处理委托给 JsRequestHandler（符合架构文档的 Handler 分离原则）
 *
 * Bridge 架构：简化版（直接 JBCefJSQuery，无 iframe 中转）
 * - JS→Java: JBCefJSQuery 直接调用 Kotlin handleJsRequest()
 * - Java→JS: executeJavaScript() 直接调用 window.ccEvents.emit()
 */
class CefBrowserPanel(private val project: Project) : Disposable {

    private val log = logger<CefBrowserPanel>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** JCEF 浏览器实例 */
    private var browser: JBCefBrowser? = null

    /** JS 查询处理器 - 用于 JS→Kotlin 通信 */
    private var jsQuery: JBCefJSQuery? = null

    /** 页面加载完成标志 */
    private var isPageLoaded = false

    /** 页面加载完成监听器列表 */
    private val loadListeners = mutableListOf<Runnable>()

    /** 页面加载监听器是否已设置 */
    private var loadListenerSetup = false

    /** Bridge 注入状态标志 */
    @Volatile
    private var isBridgeInjected = false

    /** Bridge 注入锁 */
    private val bridgeInjectionLock = Any()

    /** JS 请求处理器 */
    private val jsRequestHandler: JsRequestHandler by lazy { JsRequestHandler(project) }

    /** 连接状态 */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 是否已初始化 */
    private var isInitialized = false

    /**
     * 初始化浏览器
     */
    fun init(): JBCefBrowser {
        if (browser != null) {
            log.info("[CefBrowserPanel] Already initialized, returning existing browser")
            return browser!!
        }

        log.info("[CefBrowserPanel] Initializing JCEF browser...")
        log.info("[CefBrowserPanel] Java version: ${System.getProperty("java.version")}")
        log.info("[CefBrowserPanel] OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")

        // 使用 JBCefBrowser 的 builder 模式确保正确初始化
        browser = JBCefBrowser()

        // 确保组件可见性设置
        browser?.component?.isVisible = true
        browser?.component?.isEnabled = true

        // 诊断：检查 browser 组件状态
        log.info("[CefBrowserPanel] Browser component created: ${browser?.component != null}")
        log.info("[CefBrowserPanel] Component class: ${browser?.component?.javaClass?.name}")
        log.info("[CefBrowserPanel] Component size before add: ${browser?.component?.size}")
        log.info("[CefBrowserPanel] Component preferred size: ${browser?.component?.preferredSize}")
        log.info("[CefBrowserPanel] CefBrowser: ${browser?.getCefBrowser() != null}")

        // 设置 JS 查询回调
        setupJsQuery()

        // 设置页面加载监听
        setupLoadListener()

        // 设置请求处理器回调
        setupRequestHandlerCallbacks()

        // 诊断：启动一个监控任务，定期检查浏览器状态
        startBrowserHealthCheck()

        isInitialized = true
        log.info("[CefBrowserPanel] CefBrowserPanel initialized successfully")

        return browser!!
    }

    /**
     * 浏览器健康检查 - 诊断用
     */
    private fun startBrowserHealthCheck() {
        scope.launch {
            var checkCount = 0
            var lastUrl: String? = null
            while (isActive && !isDisposed) {
                delay(2000)
                checkCount++
                try {
                    val cefBrowser = browser?.getCefBrowser()
                    if (cefBrowser != null) {
                        val currentUrl = browser?.cefBrowser?.url
                        if (checkCount <= 10 || currentUrl != lastUrl) {
                            log.debug("[CefBrowserPanel] Health check $checkCount: cefBrowser OK, URL: $currentUrl")
                            lastUrl = currentUrl
                        }
                        // 尝试执行一个简单的 JS 来验证浏览器是否响应
                        try {
                            val testResult = cefBrowser.executeJavaScript(
                                "typeof window !== 'undefined' ? 'ok' : 'no-window'",
                                currentUrl ?: "about:blank", 0
                            )
                            if (checkCount <= 5) {
                                log.debug("[CefBrowserPanel] Health check $checkCount JS result: $testResult")
                            }
                        } catch (jsError: Exception) {
                            if (checkCount <= 5) {
                                log.warn("[CefBrowserPanel] Health check $checkCount JS failed: ${jsError.message}")
                            }
                        }
                    } else {
                        if (checkCount <= 10) {
                            log.warn("[CefBrowserPanel] Health check $checkCount: cefBrowser is null")
                        }
                    }
                } catch (e: Exception) {
                    if (checkCount <= 5) {
                        log.warn("[CefBrowserPanel] Health check $checkCount failed: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 设置请求处理器回调
     */
    private fun setupRequestHandlerCallbacks() {
        jsRequestHandler.setCallbacks(
            responseCallback = { action, queryId, response, error ->
                this@CefBrowserPanel.sendResponseToJs(action, queryId, response, error)
            },
            eventCallback = { event, data ->
                this@CefBrowserPanel.sendToJavaScript(event, data)
            }
        )
    }

    /**
     * 设置页面加载监听
     * 使用 JBCefClient 的 addLoadHandler 回调
     */
    private fun setupLoadListener() {
        if (loadListenerSetup) return
        browser?.let { b ->
            try {
                val client = b.jbCefClient
                val cefBrowser = b.getCefBrowser()

                log.info("[CefBrowserPanel] Setting up load listener...")
                log.info("[CefBrowserPanel] Client class: ${client.javaClass.name}")
                log.info("[CefBrowserPanel] CefBrowser: $cefBrowser")

                // 使用反射查找合适的 load handler 接口
                val handlerClass = Class.forName("org.cef.handler.CefLoadHandler")
                val handler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.classLoader,
                    arrayOf(handlerClass)
                ) { _, method, args ->
                    log.debug("[CefBrowserPanel] Load handler method called: ${method.name}, args: ${args?.contentToString()}")
                    when (method.name) {
                        "onLoadingStateChange" -> {
                            log.info("[CefBrowserPanel] onLoadingStateChange called with ${args?.size} args")
                            if (args != null && args.size >= 4) {
                                try {
                                    val isLoading = args[1] as Boolean
                                    val canGoBack = args[2] as Boolean
                                    val canGoForward = args[3] as Boolean
                                    log.info("[CefBrowserPanel] Loading state - isLoading: $isLoading, canGoBack: $canGoBack, canGoForward: $canGoForward")

                                    // 获取当前 URL
                                    val currentUrl = b.cefBrowser?.url ?: "unknown"
                                    if (!isLoading && currentUrl.isNotEmpty()) {
                                        log.info("[CefBrowserPanel] Page loaded: $currentUrl")
                                        isPageLoaded = true
                                        loadListeners.forEach { it.run() }
                                        loadListeners.clear()
                                    }
                                } catch (e: Exception) {
                                    log.error("[CefBrowserPanel] Error parsing onLoadingStateChange args", e)
                                }
                            }
                            null
                        }
                        "onLoadStart" -> {
                            log.info("[CefBrowserPanel] onLoadStart: ${args?.contentToString()}")
                            null
                        }
                        "onLoadEnd" -> {
                            log.info("[CefBrowserPanel] onLoadEnd: ${args?.contentToString()}")
                            if (args != null && args.size >= 3) {
                                val url = args[1] as? String ?: ""
                                val httpStatusCode = args[2] as? Int ?: 0
                                log.info("[CefBrowserPanel] Load ended - URL: $url, Status: $httpStatusCode")
                                if (httpStatusCode == 200 || httpStatusCode == 0) {
                                    isPageLoaded = true
                                    loadListeners.forEach { it.run() }
                                    loadListeners.clear()
                                }
                            }
                            null
                        }
                        "onLoadError" -> {
                            log.warn("[CefBrowserPanel] onLoadError: ${args?.contentToString()}")
                            null
                        }
                        else -> null
                    }
                }

                // 尝试不同的方法签名
                try {
                    val addLoadHandlerMethod = client.javaClass.getMethod(
                        "addLoadHandler",
                        Class.forName("org.cef.handler.CefLoadHandler"),
                        Class.forName("org.cef.browser.CefBrowser")
                    )
                    addLoadHandlerMethod.invoke(client, handler, cefBrowser)
                    loadListenerSetup = true
                    log.info("[CefBrowserPanel] Load listener setup successfully")
                } catch (e: NoSuchMethodException) {
                    // 尝试不带 CefBrowser 参数的版本
                    log.warn("[CefBrowserPanel] addLoadHandler(CefLoadHandler, CefBrowser) not found, trying single param version")
                    try {
                        val addLoadHandlerMethod2 = client.javaClass.getMethod(
                            "addLoadHandler",
                            Class.forName("org.cef.handler.CefLoadHandler")
                        )
                        addLoadHandlerMethod2.invoke(client, handler)
                        loadListenerSetup = true
                        log.info("[CefBrowserPanel] Load listener setup successfully (single param)")
                    } catch (e2: Exception) {
                        log.error("[CefBrowserPanel] Failed to setup load listener: ${e2.message}", e2)
                    }
                }
            } catch (e: Exception) {
                log.warn("[CefBrowserPanel] Failed to setup load listener (will use fallback): ${e.message}", e)
                // 不要设置 loadListenerSetup = true，让 fallback 机制处理
            }
        }
    }

    /**
     * 等待页面加载完成后执行
     */
    private fun executeWhenPageLoaded(runnable: Runnable) {
        if (isPageLoaded) {
            runnable.run()
        } else {
            loadListeners.add(runnable)
        }
    }

    /**
     * 设置 JS 查询回调 - 直接使用 JBCefJSQuery 处理请求
     */
    private fun setupJsQuery() {
        browser?.let { b ->
            jsQuery = JBCefJSQuery.create(b).also {
                it.addHandler { request ->
                    handleJsRequest(request)
                }
            }
        }
    }

    /**
     * 处理来自 JavaScript 的请求
     *
     * @param request JSON 格式的请求字符串
     * @return JBCefJSQuery.Response
     */
    private fun handleJsRequest(request: String): JBCefJSQuery.Response {
        return try {
            log.debug("Received JS request: $request")
            val handled = jsRequestHandler.handleRequest(request)
            if (handled) {
                // 异步响应已通过 callback 发送
                JBCefJSQuery.Response("")
            } else {
                log.warn("Unknown action in request: $request")
                JBCefJSQuery.Response("")
            }
        } catch (e: Exception) {
            log.error("Error handling JS request: ${e.message}", e)
            JBCefJSQuery.Response("")
        }
    }

    /**
     * 发送响应到 JavaScript
     * @param action  原始 action 名（用于调试）
     * @param queryId 查询 ID
     * @param response 响应数据（null 表示错误）
     * @param error 错误信息（可选）
     */
    private fun sendResponseToJs(action: String, queryId: Int, response: Any?, error: String? = null) {
        // java-bridge.ts 监听 'response' 事件，格式: {queryId, result, error}
        val data: Map<String, Any> = when {
            error != null -> mapOf("queryId" to queryId, "error" to error)
            response != null -> mapOf("queryId" to queryId, "result" to response)
            else -> mapOf("queryId" to queryId)
        }
        sendToJavaScript("response", data)
    }

    // ---- JavaScript Injection (简化版，无 iframe 中转) ----

    /**
     * 注入后端 JavaScript Bridge
     *
     * 简化架构：
     * - JS→Java: 直接通过 JBCefJSQuery.inject() 调用 Kotlin
     * - Java→JS: 通过 executeJavaScript() 调用 window.ccEvents.emit()
     *
     * 关键特性：
     * - 同步锁防止重复注入
     * - Pre-Registration 模式支持
     * - 10秒请求超时检测
     * - 桥接 Health Check
     */
    private fun injectBackendJavaScript() {
        // 防止重复注入
        synchronized(bridgeInjectionLock) {
            if (isBridgeInjected) {
                log.debug("[CefBrowserPanel] Bridge already injected, skipping")
                return
            }
            isBridgeInjected = true
        }

        val cefBrowser = browser?.getCefBrowser() ?: run {
            log.error("[CefBrowserPanel] Browser is null, cannot inject Bridge")
            return
        }

        // 获取 JBCefJSQuery 注入的函数名
        val injectFunction = jsQuery?.inject("msg") ?: run {
            log.error("[CefBrowserPanel] JBCefJSQuery not initialized, cannot inject bridge")
            return
        }

        log.info("[CefBrowserPanel] Injecting Bridge with function: $injectFunction")

        // 简化的 Bridge 脚本
        val bridgeScript = """
            (function() {
                if (window.ccBackend && window.ccEvents) {
                    console.log('[Bridge] Already injected, skipping');
                    return;
                }

                console.log('[Bridge] Injecting simplified Bridge...');

                // 获取 JBCefJSQuery 注入的函数引用
                var _jcefQuery = $injectFunction;

                // 挂起的请求队列
                var _pendingRequests = {};
                var REQUEST_TIMEOUT = 10000; // 10秒超时

                // ccEvents：事件总线，Kotlin sendToJavaScript() 直接调用
                window.ccEvents = {
                    handlers: {},
                    on: function(event, handler) {
                        if (!this.handlers[event]) this.handlers[event] = [];
                        this.handlers[event].push(handler);
                        return function() {
                            if (window.ccEvents && window.ccEvents.handlers && window.ccEvents.handlers[event]) {
                                window.ccEvents.handlers[event] = window.ccEvents.handlers[event].filter(function(h) { return h !== handler; });
                            }
                        };
                    },
                    off: function(event, handler) {
                        if (this.handlers[event]) {
                            this.handlers[event] = this.handlers[event].filter(function(h) { return h !== handler; });
                        }
                    },
                    emit: function(event, data) {
                        if (this.handlers[event]) {
                            this.handlers[event].forEach(function(h) {
                                try { h(data); } catch(e) { console.error('[ccEvents] Handler error:', e); }
                            });
                        }
                        // 桥接到前端 CustomEvent（让 eventBus 监听 window 事件）
                        window.dispatchEvent(new CustomEvent(event, { detail: data }));
                    }
                };

                // ccBackend.send()：通过 JBCefJSQuery 发送请求
                window.ccBackend = {
                    send: function(action, params) {
                        var queryId = 'q_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                        var payload = JSON.stringify({
                            queryId: queryId,
                            action: action,
                            params: params || {}
                        });

                        console.log('[Bridge] Sending:', action, params);

                        // 发送请求到 Kotlin
                        try {
                            _jcefQuery(payload);
                        } catch (e) {
                            console.error('[Bridge] Send error:', e);
                        }

                        // 设置超时
                        var timer = setTimeout(function() {
                            if (_pendingRequests[queryId]) {
                                console.warn('[Bridge] Request timeout:', action, queryId);
                                delete _pendingRequests[queryId];
                            }
                        }, REQUEST_TIMEOUT);

                        _pendingRequests[queryId] = { timer: timer, action: action };
                    },
                    // 响应处理（由 Kotlin 通过 ccEvents.emit('response', ...) 调用）
                    _handleResponse: function(data) {
                        if (data && data.queryId && _pendingRequests[data.queryId]) {
                            var pending = _pendingRequests[data.queryId];
                            clearTimeout(pending.timer);
                            delete _pendingRequests[data.queryId];
                            console.log('[Bridge] Response received:', pending.action, data.queryId);
                        }
                    },
                    // 健康检查
                    _healthCheck: function(timestamp) {
                        console.log('[Bridge] Health check:', timestamp);
                    },
                    isDevMode: true
                };

                // 监听 response 事件以清理挂起的请求
                window.ccEvents.on('response', window.ccBackend._handleResponse);

                console.log('[Bridge] ccBackend injected successfully');
            })();
        """.trimIndent()

        // 执行 Bridge 注入脚本
        val currentUrl = browser?.cefBrowser?.url ?: "about:blank"
        cefBrowser.executeJavaScript(bridgeScript, currentUrl, 0)
        log.info("Bridge injected successfully")

        // 启动健康检查
        startBridgeHealthCheck()
    }

    /**
     * Bridge 健康检查
     */
    private fun startBridgeHealthCheck() {
        scope.launch {
            while (isActive && !isDisposed) {
                delay(5000) // 5秒心跳
                try {
                    val cefBrowser = browser?.getCefBrowser()
                    if (cefBrowser != null) {
                        val currentUrl = browser?.cefBrowser?.url ?: "about:blank"
                        val jsCode = """
                            if (window.ccBackend && window.ccBackend._healthCheck) {
                                window.ccBackend._healthCheck(new Date().toISOString());
                            }
                        """.trimIndent()
                        cefBrowser.executeJavaScript(jsCode, currentUrl, 0)
                    }
                } catch (e: Exception) {
                    log.warn("Bridge health check failed", e)
                }
            }
        }
    }

    /** dispose 状态标志 */
    @Volatile
    private var isDisposed = false

    // ---- Java → JS Communication ----

    /**
     * 发送数据到 JavaScript
     * 使用 executeJavaScript 直接调用 window.ccEvents.emit()
     *
     * @param event 事件名称
     * @param data 数据
     */
    fun sendToJavaScript(event: String, data: Map<String, Any>) {
        browser?.let { b ->
            val cefBrowser = b.getCefBrowser()
            try {
                val jsonData = JsonUtils.toJson(data)
                // 安全转义 event 名称防止注入
                val safeEvent = event.replace("\\", "\\\\").replace("'", "\\'")
                // 安全转义 JSON 字符串
                val safeJsonForJs = jsonData
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "")

                val js = "window.ccEvents && window.ccEvents.emit('$safeEvent', JSON.parse('$safeJsonForJs'));"
                val currentUrl = b.cefBrowser?.url ?: "about:blank"
                cefBrowser.executeJavaScript(js, currentUrl, 0)
            } catch (e: Exception) {
                log.error("Error sending to JavaScript: ${e.message}", e)
            }
        }
    }

    /**
     * 加载 HTML 页面
     */
    fun loadHtmlPage(url: String) {
        // 重置状态
        isPageLoaded = false
        isBridgeInjected = false

        log.info("[CefBrowserPanel] Loading URL: $url")
        log.info("[CefBrowserPanel] Browser component valid: ${browser?.component != null}")
        log.info("[CefBrowserPanel] Component displayable: ${browser?.component?.isDisplayable}")
        log.info("[CefBrowserPanel] Component showing: ${browser?.component?.isShowing}")

        // 验证 CefBrowser 是否可用
        val cefBrowser = browser?.getCefBrowser()
        if (cefBrowser == null) {
            log.error("[CefBrowserPanel] CefBrowser is null! Cannot load URL")
            return
        }

        // 启动一个协程来等待组件可显示后再加载 URL
        scope.launch {
            // 等待组件变成 displayable（已添加到显示层级）
            var waited = 0
            while (browser?.component?.isDisplayable != true && waited < 50) {
                delay(100)
                waited++
            }

            if (waited >= 50) {
                log.warn("[CefBrowserPanel] Component not displayable after 5 seconds, loading anyway")
            } else {
                log.info("[CefBrowserPanel] Component became displayable after ${waited * 100}ms")
            }

            // 直接使用 loadURL，JCEF 可以正确处理 HTTP URL
            log.info("[CefBrowserPanel] Calling loadURL on browser...")
            browser?.loadURL(url)

            log.info("[CefBrowserPanel] loadURL called, current URL: ${browser?.cefBrowser?.url}")
        }

        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable {
            log.info("[CefBrowserPanel] Page load event received, injecting bridge")
            injectBackendJavaScript()
        })

        // 后备：如果5秒后仍未加载完成，强制注入
        scope.launch {
            delay(5000)
            if (!isPageLoaded) {
                log.warn("[CefBrowserPanel] Page load timeout (5s), forcing bridge injection")
                log.warn("[CefBrowserPanel] Current URL at timeout: ${browser?.cefBrowser?.url}")
                isPageLoaded = true
                injectBackendJavaScript()
            }
        }
    }

    /**
     * 加载本地 HTML 内容
     */
    fun loadHtmlContent(htmlContent: String, baseUrl: String = "http://localhost/") {
        // 重置状态
        isPageLoaded = false
        isBridgeInjected = false

        log.info("[CefBrowserPanel] Loading HTML content with baseUrl: $baseUrl")

        browser?.loadHTML(htmlContent, baseUrl)

        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable {
            log.info("[CefBrowserPanel] HTML content loaded, injecting bridge")
            injectBackendJavaScript()
        })

        // 后备：如果5秒后仍未加载完成，强制注入
        scope.launch {
            delay(5000)
            if (!isPageLoaded) {
                log.warn("[CefBrowserPanel] HTML load timeout (5s), forcing bridge injection")
                isPageLoaded = true
                injectBackendJavaScript()
            }
        }
    }

    // ---- Lifecycle ----

    override fun dispose() {
        isDisposed = true
        scope.cancel()
        jsQuery?.dispose()
        browser?.dispose()
        browser = null
        jsQuery = null
        isInitialized = false
        log.info("CefBrowserPanel disposed")
    }
}
