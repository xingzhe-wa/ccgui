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
 */
class CefBrowserPanel(private val project: Project) : Disposable {

    private val log = logger<CefBrowserPanel>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** JCEF 浏览器实例 */
    private var browser: JBCefBrowser? = null

    /** JS 查询处理器 */
    private var jsQuery: JBCefJSQuery? = null

    /** JS 调用后端的全局函数引用，由 injectBackendJavaScript() 设置 */
    private var jsQueryInvoker: java.util.function.Function<String, JBCefJSQuery.Response>? = null

    /** 页面加载完成标志 */
    private var isPageLoaded = false

    /** 页面加载完成监听器列表 */
    private val loadListeners = mutableListOf<Runnable>()

    /** 页面加载监听器是否已设置 */
    private var loadListenerSetup = false

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
     * 设置 JS 查询回调
     */
    private fun setupJsQuery() {
        browser?.let { b ->
            jsQueryInvoker = java.util.function.Function<String, JBCefJSQuery.Response> { request ->
                handleJsRequest(request)
            }
            jsQuery = JBCefJSQuery.create(b).also {
                it.addHandler(jsQueryInvoker!!)
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
            val handled = jsRequestHandler.handleRequest(request)
            if (handled) {
                // 异步响应已通过 callback 发送
                JBCefJSQuery.Response("")
            } else {
                // 未知 action
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

    // ---- JavaScript Injection ----

    /**
     * 注入后端 JavaScript Bridge
     *
     * JS→Java 通信方案：
     * 我们不依赖 JBCefJSQuery 的内部机制（其函数名随机且不可预测）。
     * 改用自定义 iframe + postMessage 通道：
     * - 注入一个隐藏 iframe，加载 data URL，内含 JS 脚本
     * - 该脚本定义 __ccJavaRequest(request) 函数
     * - 通过 iframe.contentWindow.postMessage 发送请求到主页面
     * - 主页面收到消息后，调用 window.javaRequestCallback（由 Kotlin 设置）
     * - Kotlin handler 执行后，通过 sendToJavaScript("actionResponse:xxx", data) 返回结果
     *
     * Java→JS 通信：sendToJavaScript() 直接调用 window.ccEvents.emit()
     */
    private fun injectBackendJavaScript() {
        browser?.let { b ->
            // iframe 的 data URL：内嵌脚本监听 postMessage，收到后调用主页面回调
            val iframeDataUrl = (
                "data:text/html;charset=utf-8," +
                "<script>" +
                "window.addEventListener('message',function(e){" +
                "if(e.data&&e.data.type==='cc-java-request'){" +
                "var req=e.data.request;" +
                "var result='';" +
                "if(typeof window.javaRequestCallback==='function'){" +
                "result=window.javaRequestCallback(req);" +
                "}" +
                "e.source.postMessage({type:'cc-java-response',requestId:e.data.requestId,result:result},'*');" +
                "}" +
                "});" +
                "window.parent.postMessage({type:'cc-bridge-ready'},'*');" +
                "</script>"
                ).replace("\n", "")

            // 主页面 bridge 脚本：设置 ccBackend.send() 和 ccEvents
            val bridgeScript = """
                (function() {
                    if (window.ccBackend && window.ccEvents) return;

                    var pendingRequests = {};
                    var requestCounter = 0;
                    var bridgeReady = false;

                    // 监听 iframe 就绪信号
                    window.addEventListener('message', function(e) {
                        if (e.data && e.data.type === 'cc-bridge-ready') {
                            bridgeReady = true;
                            console.log('[CCBackend] Bridge ready');
                        }
                        // 接收来自 iframe 的响应
                        if (e.data && e.data.type === 'cc-java-response' && e.data.requestId) {
                            var callback = pendingRequests[e.data.requestId];
                            if (callback) {
                                delete pendingRequests[e.data.requestId];
                                callback(e.data.result);
                            }
                        }
                    });

                    // Hook ccEvents.emit()：拦截 'response' 事件，将响应路由到 pendingRequests
                    // java-bridge.ts 发送: ccEvents.on('response', ({queryId, result, error}) => ...)
                    if (typeof window.ccEvents !== 'undefined') {
                        var origEmit = window.ccEvents.emit.bind(window.ccEvents);
                        var responseInterceptor = function(event, data) {
                            if (event === 'response' && data && data.queryId) {
                                var cb = pendingRequests[data.queryId];
                                if (cb) {
                                    delete pendingRequests[data.queryId];
                                    cb(data.result);
                                }
                            }
                        };
                        window.ccEvents.emit = function(event, data) {
                            responseInterceptor(event, data);
                            return origEmit(event, data);
                        };
                    }

                    // ccBackend.send()：通过隐藏 iframe 发送请求到 Kotlin
                    window.ccBackend = {
                        send: function(request) {
                            if (!bridgeReady) {
                                console.warn('[CCBackend] Bridge not ready, request dropped');
                                return;
                            }
                            var iframe = document.getElementById('__cc_bridge_iframe__');
                            if (!iframe || !iframe.contentWindow) {
                                console.warn('[CCBackend] Bridge iframe not found');
                                return;
                            }
                            var requestId = ++requestCounter;
                            // 等待响应（iframe 通过 postMessage 返回）
                            pendingRequests[requestId] = function(result) {
                                // 响应由 Kotlin sendToJavaScript("actionResponse:xxx", data) 处理
                                // 这里不做任何事，因为所有响应都通过 ccEvents.emit() 推送
                            };
                            iframe.contentWindow.postMessage({
                                type: 'cc-java-request',
                                requestId: requestId,
                                request: typeof request === 'string' ? request : JSON.stringify(request)
                            }, '*');
                        }
                    };

                    // ccEvents：事件总线，Kotlin sendToJavaScript() 直接调用
                    // 同时使用 CustomEvent 桥接到前端 eventBus
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
                                this.handlers[event].forEach(function(h) { h(data); });
                            }
                            // 桥接到前端 CustomEvent（让 eventBus 监听 window 事件）
                            window.dispatchEvent(new CustomEvent(event, { detail: data }));
                        }
                    };
                })();
            """.trimIndent()

            // Step 1: 创建隐藏 iframe（用于 JS→Kotlin 通信）
            b.getCefBrowser().executeJavaScript(
                """
                (function() {
                    if (document.getElementById('__cc_bridge_iframe__')) return;
                    var iframe = document.createElement('iframe');
                    iframe.id = '__cc_bridge_iframe__';
                    iframe.src = '$iframeDataUrl';
                    iframe.style.display = 'none';
                    iframe.width = '0';
                    iframe.height = '0';
                    iframe.setAttribute('sandbox', 'allow-scripts');
                    document.body ? document.body.appendChild(iframe) : document.addEventListener('DOMContentLoaded', function() { document.body.appendChild(iframe); });
                })();
                """.trimIndent(),
                b.getCefBrowser().getURL(),
                0
            )

            // Step 2: 注入 ccBackend 和 ccEvents
            b.getCefBrowser().executeJavaScript(bridgeScript, b.getCefBrowser().getURL(), 0)

            // Step 3: 设置 window.javaRequestCallback（JS→Kotlin 的实际触发点）
            // 使用 loadURL/javascript: 机制触发 JBCefJSQuery
            // JBCefJSQuery 的注入脚本会创建 __jcef_query_<id>__ 函数
            // 我们找到它并通过 location.href=javascript: 调用它
            b.getCefBrowser().executeJavaScript(
                """
                (function() {
                    var retryCount = 0;
                    function setupCallback() {
                        // 查找 JBCefJSQuery 注入的 __jcef_query_<id>__ 函数
                        var keys = Object.keys(window).filter(function(k) { return k.indexOf('__jcef_query__') === 0; });
                        if (keys.length > 0) {
                            var fn = window[keys[0]];
                            if (typeof fn === 'function') {
                                window.javaRequestCallback = function(request) {
                                    try {
                                        var result = fn(request);
                                        return typeof result === 'string' ? result : JSON.stringify(result || '');
                                    } catch(ex) {
                                        console.error('[CCBackend] javaRequestCallback error:', ex);
                                        return '';
                                    }
                                };
                                console.log('[CCBackend] javaRequestCallback connected to JBCefJSQuery');
                                return true;
                            }
                        }
                        if (retryCount < 30) {
                            retryCount++;
                            setTimeout(setupCallback, 100);
                        } else {
                            console.warn('[CCBackend] JBCefJSQuery not found after 3s');
                        }
                        return false;
                    }
                    setupCallback();
                })();
                """.trimIndent(),
                b.getCefBrowser().getURL(),
                0
            )
        }
    }

    // ---- Java → JS Communication ----

    /**
     * 发送数据到 JavaScript
     * 使用 loadURL 执行 JavaScript 代码
     *
     * @param event 事件名称
     * @param data 数据
     */
    fun sendToJavaScript(event: String, data: Map<String, Any>) {
        browser?.let { b ->
            val jsonData = JsonUtils.toJson(data)
            // Escape single quotes and backslashes in event name to prevent injection
            val safeEvent = event.replace("\\", "\\\\").replace("'", "\\'")
            // Escape JSON string for safe embedding in JS single-quoted string
            val safeJsonForJs = jsonData
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            val js = "window.ccEvents && window.ccEvents.emit('$safeEvent', JSON.parse('$safeJsonForJs'));"
            // Use getCefBrowser().executeJavaScript() to avoid XSS via loadURL("javascript:...")
            b.getCefBrowser().executeJavaScript(js, b.getCefBrowser().getURL(), 0)
        }
    }

    /**
     * 加载 HTML 页面
     */
    fun loadHtmlPage(url: String) {
        // 重置加载状态
        isPageLoaded = false
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
        // 重置加载状态
        isPageLoaded = false
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
        scope.cancel()
        jsQuery?.dispose()
        browser?.dispose()
        browser = null
        jsQuery = null
        isInitialized = false
        log.info("CefBrowserPanel disposed")
    }
}