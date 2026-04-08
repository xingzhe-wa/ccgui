package com.github.xingzhewa.ccgui.browser

import com.github.xingzhewa.ccgui.bridge.BridgeManager
import com.github.xingzhewa.ccgui.bridge.ConnectionState
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JCEF 浏览器面板封装
 *
 * 提供 Java → JS 和 JS → Java 双向通信
 */
class CefBrowserPanel(private val project: Project) : Disposable {

    private val log = logger<CefBrowserPanel>()

    /** JCEF 浏览器实例 */
    private var browser: JBCefBrowser? = null

    /** JS 查询处理器 */
    private var jsQuery: JBCefJSQuery? = null

    /** BridgeManager */
    private val bridgeManager: BridgeManager by lazy { BridgeManager.getInstance(project) }

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

        isInitialized = true
        log.info("CefBrowserPanel initialized")

        return browser!!
    }

    /**
     * 设置 JS 查询回调
     */
    private fun setupJsQuery() {
        browser?.let { b ->
            jsQuery = JBCefJSQuery.create(b)
            jsQuery?.addHandler(java.util.function.Function<String, JBCefJSQuery.Response> { request ->
                handleJsRequest(request)
            })
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
            val json = JsonUtils.parseObject(request) ?: return JBCefJSQuery.Response("")
            val queryId = json.get("queryId")?.asInt ?: 0
            val action = json.get("action")?.asString ?: ""
            val params = json.get("params")

            log.debug("JS request: action=$action, queryId=$queryId")

            // 处理各种 action
            val response = when (action) {
                "sendMessage" -> handleSendMessage(queryId, params)
                "streamMessage" -> handleStreamMessage(queryId, params)
                "cancelStreaming" -> handleCancelStreaming(queryId, params)
                "getConfig" -> handleGetConfig(queryId, params)
                "setConfig" -> handleSetConfig(queryId, params)
                "createSession" -> handleCreateSession(queryId, params)
                "switchSession" -> handleSwitchSession(queryId, params)
                "deleteSession" -> handleDeleteSession(queryId, params)
                "getThemes" -> handleGetThemes(queryId)
                "submitAnswer" -> handleSubmitAnswer(queryId, params)
                else -> {
                    log.warn("Unknown action: $action")
                    null
                }
            }

            // 通过事件系统发送响应
            sendResponseToJs(queryId, response)
            // 返回空响应表示我们已经通过事件系统处理了响应
            JBCefJSQuery.Response("")
        } catch (e: Exception) {
            log.error("Error handling JS request: ${e.message}", e)
            JBCefJSQuery.Response("")
        }
    }

    /**
     * 发送响应到 JavaScript
     */
    private fun sendResponseToJs(queryId: Int, response: Any?) {
        val data = if (response != null) {
            mapOf("queryId" to queryId, "result" to response)
        } else {
            mapOf("queryId" to queryId)
        }
        sendToJavaScript("response", data)
    }

    // ---- Action Handlers ----

    private fun handleSendMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val message = params?.asJsonObject?.get("message")?.asString ?: ""
        bridgeManager.sendMessage(message, "", object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
            override fun onLineReceived(line: String) {
                sendToJavaScript("stream", mapOf("chunk" to line))
            }

            override fun onStreamComplete(messages: List<String>) {
                sendToJavaScript("streamComplete", mapOf("messages" to messages))
            }

            override fun onStreamError(error: String) {
                sendToJavaScript("streamError", mapOf("error" to error))
            }
        })
        return null
    }

    private fun handleStreamMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val message = params?.asJsonObject?.get("message")?.asString ?: ""
        bridgeManager.streamMessage(message, "", object : com.github.xingzhewa.ccgui.bridge.StreamCallback {
            override fun onLineReceived(line: String) {
                sendToJavaScript("stream", mapOf("chunk" to line))
            }

            override fun onStreamComplete(messages: List<String>) {
                sendToJavaScript("streamComplete", mapOf("messages" to messages))
            }

            override fun onStreamError(error: String) {
                sendToJavaScript("streamError", mapOf("error" to error))
            }
        })
        return null
    }

    private fun handleCancelStreaming(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: ""
        bridgeManager.cancelStreaming(sessionId)
        return null
    }

    private fun handleGetConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现配置获取
        return emptyMap<String, Any>()
    }

    private fun handleSetConfig(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现配置设置
        return null
    }

    private fun handleCreateSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现创建会话
        return mapOf("id" to "sess_new")
    }

    private fun handleSwitchSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现切换会话
        return null
    }

    private fun handleDeleteSession(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现删除会话
        return null
    }

    private fun handleGetThemes(queryId: Int): Any? {
        // TODO: 实现获取主题
        return emptyList<Any>()
    }

    private fun handleSubmitAnswer(queryId: Int, params: com.google.gson.JsonElement?): Any? {
        // TODO: 实现提交答案
        return null
    }

    // ---- Response Helpers ----

    private fun createErrorResponse(queryId: Int, error: String): String {
        return JsonObject().apply {
            addProperty("queryId", queryId)
            addProperty("error", error)
        }.toString()
    }

    // ---- JavaScript Injection ----

    /**
     * 注入后端 JavaScript
     * 通过加载包含内联脚本的 HTML 数据 URL 来注入
     */
    private fun injectBackendJavaScript() {
        browser?.let { b ->
            // 创建包含内联 JavaScript 的 HTML
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <script>
                        window.ccBackend = {
                            send: function(request) {
                                window.__javaQuery && window.__javaQuery.invoke(JSON.stringify(request));
                            }
                        };
                        window.ccEvents = {
                            handlers: {},
                            on: function(event, handler) {
                                if (!this.handlers[event]) this.handlers[event] = [];
                                this.handlers[event].push(handler);
                                return () => {
                                    this.handlers[event] = this.handlers[event].filter(h => h !== handler);
                                };
                            },
                            off: function(event, handler) {
                                if (this.handlers[event]) {
                                    this.handlers[event] = this.handlers[event].filter(h => h !== handler);
                                }
                            },
                            emit: function(event, data) {
                                if (this.handlers[event]) {
                                    this.handlers[event].forEach(h => h(data));
                                }
                            }
                        };
                        console.log('CCBackend JavaScript injected');
                    </script>
                </head>
                <body></body>
                </html>
            """.trimIndent()

            b.loadHTML(htmlContent, "http://localhost/")
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
            // 使用 javascript: URL 执行 JavaScript
            b.loadURL("javascript:window.ccEvents && window.ccEvents.emit('$event', $jsonData);")
        }
    }

    /**
     * 加载 HTML 页面
     */
    fun loadHtmlPage(url: String) {
        browser?.loadURL(url)
    }

    /**
     * 加载本地 HTML 内容
     */
    fun loadHtmlContent(htmlContent: String, baseUrl: String = "http://localhost/") {
        browser?.loadHTML(htmlContent, baseUrl)
    }

    // ---- Lifecycle ----

    override fun dispose() {
        jsQuery?.dispose()
        browser?.dispose()
        browser = null
        jsQuery = null
        isInitialized = false
        log.info("CefBrowserPanel disposed")
    }
}