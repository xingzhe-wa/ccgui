package com.github.claudecode.ccgui.toolWindow

import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress

/**
 * 内嵌 HTTP 服务器
 *
 * 使用 Java 内置的 com.sun.net.httpserver 提供 React 前端静态文件
 * 解决 JCEF 无法加载 file:// URL 的问题
 */
class FrontendHttpServer(private val documentRoot: File) : Disposable {
    private val log = logger<FrontendHttpServer>()
    private var server: com.sun.net.httpserver.HttpServer? = null
    private var serverPort: Int = 0

    val baseUrl: String
        get() = "http://localhost:$serverPort"

    /**
     * 启动 HTTP 服务器
     */
    fun start(): String {
        if (server != null) {
            return baseUrl
        }

        // 找一个可用端口
        serverPort = findAvailablePort()

        log.info("[FrontendHttpServer] Starting HTTP server on port $serverPort")
        log.info("[FrontendHttpServer] Document root: ${documentRoot.absolutePath}")

        server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", serverPort), 0)

        // 创建上下文处理器
        server!!.createContext("/", StaticFileHandler(documentRoot))

        server!!.executor = null // 使用默认 executor

        server!!.start()
        log.info("[FrontendHttpServer] HTTP server started at $baseUrl")

        return baseUrl
    }

    /**
     * 停止 HTTP 服务器
     */
    fun stop() {
        server?.let {
            log.info("[FrontendHttpServer] Stopping HTTP server")
            it.stop(1)
            server = null
        }
    }

    override fun dispose() {
        stop()
    }

    /**
     * 找一个可用的端口
     */
    private fun findAvailablePort(): Int {
        return try {
            val socket = java.net.ServerSocket(0)
            val port = socket.localPort
            socket.close()
            port
        } catch (e: Exception) {
            log.warn("[FrontendHttpServer] Failed to find available port, using default 8080")
            8080
        }
    }
}

/**
 * 静态文件处理器
 */
private class StaticFileHandler(private val documentRoot: File) : com.sun.net.httpserver.HttpHandler {
    private val log = logger<StaticFileHandler>()

    override fun handle(exchange: com.sun.net.httpserver.HttpExchange) {
        try {
            val requestPath = exchange.requestURI.path
            val normalizedPath = if (requestPath == "/") "/index.html" else requestPath

            // 安全检查：防止路径遍历攻击
            val cleanPath = normalizedPath.replace("..", "").removePrefix("/")
            val file = File(documentRoot, cleanPath)

            // 验证文件在文档根目录内
            if (!file.canonicalPath.startsWith(documentRoot.canonicalPath)) {
                send404(exchange, "Forbidden")
                return
            }

            if (!file.exists() || !file.isFile) {
                // 尝试 index.html
                val indexFile = File(documentRoot, cleanPath.removeSuffix("/") + "/index.html")
                if (indexFile.exists() && indexFile.isFile) {
                    serveFile(exchange, indexFile)
                } else {
                    send404(exchange, "Not Found: $normalizedPath")
                }
                return
            }

            serveFile(exchange, file)
        } catch (e: Exception) {
            log.error("[StaticFileHandler] Error handling request", e)
            send500(exchange, e.message ?: "Internal error")
        } finally {
            exchange.close()
        }
    }

    private fun serveFile(exchange: com.sun.net.httpserver.HttpExchange, file: File) {
        val mimeType = getMimeType(file.name)

        exchange.responseHeaders["Content-Type"] = listOf(mimeType)
        exchange.responseHeaders["Cache-Control"] = listOf("no-cache, no-store, must-revalidate")
        exchange.responseHeaders["Access-Control-Allow-Origin"] = listOf("*")

        val content = file.readBytes()
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, content.size.toLong())

        exchange.responseBody.use { output ->
            output.write(content)
        }

        log.debug("[StaticFileHandler] Served: ${file.name} (${content.size} bytes)")
    }

    private fun send404(exchange: com.sun.net.httpserver.HttpExchange, message: String) {
        val body = "<html><body><h1>404 Not Found</h1><p>$message</p></body></html>"
        exchange.responseHeaders["Content-Type"] = listOf("text/html; charset=utf-8")
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }

    private fun send500(exchange: com.sun.net.httpserver.HttpExchange, message: String) {
        val body = "<html><body><h1>500 Internal Error</h1><p>$message</p></body></html>"
        exchange.responseHeaders["Content-Type"] = listOf("text/html; charset=utf-8")
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html") -> "text/html; charset=utf-8"
            fileName.endsWith(".js") -> "application/javascript; charset=utf-8"
            fileName.endsWith(".css") -> "text/css; charset=utf-8"
            fileName.endsWith(".json") -> "application/json"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".svg") -> "image/svg+xml"
            fileName.endsWith(".ico") -> "image/x-icon"
            fileName.endsWith(".woff") -> "font/woff"
            fileName.endsWith(".woff2") -> "font/woff2"
            fileName.endsWith(".ttf") -> "font/ttf"
            fileName.endsWith(".eot") -> "application/vnd.ms-fontobject"
            else -> "application/octet-stream"
        }
    }
}
