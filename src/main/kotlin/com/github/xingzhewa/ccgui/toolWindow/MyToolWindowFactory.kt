package com.github.xingzhewa.ccgui.toolWindow

import com.github.xingzhewa.ccgui.bridge.BridgeManager
import com.github.xingzhewa.ccgui.bridge.SimpleStreamCallback
import com.github.xingzhewa.ccgui.bridge.StreamCallback
import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.util.logger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CCGUI 工具窗口工厂
 */
class MyToolWindowFactory : ToolWindowFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger<MyToolWindowFactory>().info("Creating CCGUI tool window content")

        // 创建浏览器面板
        var browserPanelRef: CefBrowserPanel? = null
        val browserPanel = CefBrowserPanel(project) { event, data ->
            handleMessageFromJS(project, event, data, browserPanelRef)
        }
        browserPanelRef = browserPanel

        // 加载构建后的 React 应用
        loadReactApp(browserPanel)

        // 添加到工具窗口
        val content = ContentFactory.getInstance().createContent(browserPanel, "", false)
        toolWindow.contentManager.addContent(content)

        logger<MyToolWindowFactory>().info("CCGUI tool window created successfully")
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * 加载 React 应用（内联方式，避免 file:// 协议问题）
     */
    private fun loadReactApp(browserPanel: CefBrowserPanel) {
        try {
            // 1. 读取 index.html（使用 getResourceAsStream，支持 JAR）
            val htmlContent = javaClass.getResourceAsStream("/dist/webview/index.html")
                ?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("index.html not found")

            // 2. 解析 CSS 和 JS 文件名
            val cssPattern = Regex("""href=".*/(index\.[^"]+\.css)"""")
            val jsPattern = Regex("""src=".*/(index\.[^"]+\.js)"""")

            val cssFileName = cssPattern.find(htmlContent)?.groupValues?.get(1)
                ?: throw IllegalStateException("CSS file not found in index.html")
            val jsFileName = jsPattern.find(htmlContent)?.groupValues?.get(1)
                ?: throw IllegalStateException("JS file not found in index.html")

            // 3. 读取 CSS 和 JS 内容（使用 getResourceAsStream，支持 JAR）
            val cssContent = javaClass.getResourceAsStream("/dist/webview/assets/$cssFileName")
                ?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("CSS resource not found")
            val jsContent = javaClass.getResourceAsStream("/dist/webview/assets/$jsFileName")
                ?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("JS resource not found")

            // 4. 构建内联 HTML（使用 buildString，完全避免 replaceFirst 和字符串插值）
            val inlineHtml = buildString {
                // 构建完整 HTML，只插入 CSS 和 JS，不处理内容中的特殊字符
                append("<!DOCTYPE html><html><head>")
                append("<meta charset=\"UTF-8\"><title>CCGUI</title>")
                append("<style>")
                append(cssContent)  // 直接追加 CSS 内容
                append("</style></head><body>")
                append("<div id=\"root\"></div>")
                append("<script type=\"module\">")
                append(jsContent)   // 直接追加 JS 内容
                append("</script></body></html>")
            }

            logger<MyToolWindowFactory>().info("Loading React app with inline resources, size: ${inlineHtml.length} bytes")

            // 5. 加载 HTML
            browserPanel.loadHtmlPage(inlineHtml)
        } catch (e: Exception) {
            logger<MyToolWindowFactory>().error("Failed to load React app", e)
            browserPanel.loadHtmlPage(getErrorHtml(e.message ?: "Unknown error"))
        }
    }

    /**
     * 处理来自 JavaScript 的消息
     */
    private fun handleMessageFromJS(
        project: Project,
        event: String,
        data: String?,
        browserPanel: CefBrowserPanel?
    ): JBCefJSQuery.Response? {
        logger<MyToolWindowFactory>().info("Handling message: event=$event, data=$data")

        return when (event) {
            "getConfig" -> {
                // 返回配置信息
                JBCefJSQuery.Response("""{"theme":{"dark":false},"font":{"family":"JetBrains Mono","size":14},"locale":"zh-CN"}""")
            }
            "sendMessage" -> {
                // 解析消息数据
                val messageData = try {
                    gson.fromJson(data, JsonObject::class.java)
                } catch (e: Exception) {
                    logger<MyToolWindowFactory>().error("Failed to parse message data", e)
                    null
                }

                if (messageData != null && browserPanel != null) {
                    val message = messageData.get("message")?.asString ?: ""
                    val sessionId = messageData.get("sessionId")?.asString

                    // 异步发送消息
                    scope.launch {
                        sendMessageToBridge(project, message, sessionId, browserPanel)
                    }
                }

                // 立即返回，后台异步处理
                JBCefJSQuery.Response("""{"status":"sending"}""")
            }
            else -> {
                logger<MyToolWindowFactory>().warn("Unknown event: $event")
                JBCefJSQuery.Response(null)
            }
        }
    }

    /**
     * 发送消息到 Bridge
     */
    private suspend fun sendMessageToBridge(
        project: Project,
        message: String,
        sessionId: String?,
        browserPanel: CefBrowserPanel
    ) {
        val bridgeManager = project.service<BridgeManager>()

        // 创建流回调，将响应发送回前端
        val callback = object : SimpleStreamCallback() {
            override fun onLineReceived(line: String) {
                // 发送流式内容到前端
                browserPanel.sendToJavaScript("contentDelta", line)
            }

            override fun onStreamComplete(allLines: List<String>) {
                // 发送完成事件
                browserPanel.sendToJavaScript("streamEnd", mapOf("sessionId" to (sessionId ?: "")))
            }

            override fun onStreamError(error: String) {
                // 发送错误事件
                browserPanel.sendToJavaScript("error", mapOf("error" to error))
            }
        }

        try {
            // 发送消息
            val result = bridgeManager.sendMessage(
                message = message,
                sessionId = sessionId ?: "",
                callback = callback
            )

            if (result.isFailure) {
                logger<MyToolWindowFactory>().error("Failed to send message: ${result.exceptionOrNull()?.message}")
                browserPanel.sendToJavaScript("error", mapOf("error" to (result.exceptionOrNull()?.message ?: "Unknown error")))
            }
        } catch (e: Exception) {
            logger<MyToolWindowFactory>().error("Exception sending message", e)
            browserPanel.sendToJavaScript("error", mapOf("error" to e.message))
        }
    }

    /**
     * 获取错误页面 HTML
     */
    private fun getErrorHtml(error: String?): String {
        return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>CCGUI - Error</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    background: #1a1a1a;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    color: #ff6b6b;
                }
                .error-container {
                    text-align: center;
                    padding: 40px;
                }
                h1 { font-size: 24px; margin-bottom: 16px; }
                p { font-size: 14px; opacity: 0.8; }
                .error-icon { font-size: 48px; margin-bottom: 20px; }
            </style>
        </head>
        <body>
            <div class="error-container">
                <div class="error-icon">⚠️</div>
                <h1>加载失败</h1>
                <p>${error ?: "无法加载 CCGUI 界面"}</p>
                <p style="font-size: 12px; color: #888;">请尝试重新加载插件或查看日志</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}

