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
            return browser!!
        }

        browser = JBCefBrowser()

        // 设置 JS 查询回调
        setupJsQuery()

        // 设置页面加载监听
        setupLoadListener()

        // 设置请求处理器回调
        setupRequestHandlerCallbacks()

        isInitialized = true
        log.info("CefBrowserPanel initialized")

        return browser!!
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
                // 使用反射查找合适的 load handler 接口
                val handlerClass = Class.forName("org.cef.handler.CefLoadHandler")
                val handler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.classLoader,
                    arrayOf(handlerClass)
                ) { _, method, args ->
                    when (method.name) {
                        "onLoadingStateChange" -> {
                            if (args.size >= 4) {
                                val isLoading = args[0] as Boolean
                                val url = args[1] as String
                                if (!isLoading && url.isNotEmpty()) {
                                    log.debug("Page loaded: $url")
                                    isPageLoaded = true
                                    loadListeners.forEach { it.run() }
                                    loadListeners.clear()
                                }
                            }
                            null
                        }
                        else -> null
                    }
                }
                val addLoadHandlerMethod = client.javaClass.getMethod(
                    "addLoadHandler",
                    Class.forName("org.cef.handler.CefLoadHandler"),
                    Class.forName("org.cef.browser.CefBrowser")
                )
                addLoadHandlerMethod.invoke(client, handler, b.getCefBrowser())
                loadListenerSetup = true
            } catch (e: Exception) {
                log.warn("Failed to setup load listener: ${e.message}")
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
                log.debug("Bridge already injected, skipping")
                return
            }
            isBridgeInjected = true
        }

        val cefBrowser = browser?.getCefBrowser() ?: run {
            log.warn("Browser is null, cannot inject Bridge")
            return
        }

        // 获取 JBCefJSQuery 注入的函数名
        val injectFunction = jsQuery?.inject("msg") ?: run {
            log.error("JBCefJSQuery not initialized")
            return
        }

        log.info("Injecting Bridge with function: $injectFunction")

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
        cefBrowser.executeJavaScript(bridgeScript, cefBrowser.url, 0)
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
                    val jsCode = """
                        if (window.ccBackend && window.ccBackend._healthCheck) {
                            window.ccBackend._healthCheck(new Date().toISOString());
                        }
                    """.trimIndent()
                    browser?.getCefBrowser()?.executeJavaScript(jsCode, browser?.getCefBrowser()?.url, 0)
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
                cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
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

        browser?.loadURL(url)

        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable { injectBackendJavaScript() })

        // 后备：如果3秒后仍未加载完成，强制注入
        scope.launch {
            delay(3000)
            if (!isPageLoaded) {
                log.warn("Page load timeout, forcing bridge injection")
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

        browser?.loadHTML(htmlContent, baseUrl)

        // 等待页面加载完成后注入 Bridge（带后备超时）
        executeWhenPageLoaded(Runnable { injectBackendJavaScript() })

        // 后备：如果3秒后仍未加载完成，强制注入
        scope.launch {
            delay(3000)
            if (!isPageLoaded) {
                log.warn("Page load timeout, forcing bridge injection")
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
