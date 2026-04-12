package com.github.claudecode.ccgui.toolWindow

import com.github.claudecode.ccgui.browser.CefBrowserPanel
import com.github.claudecode.ccgui.application.streaming.StreamingOutputEngine
import com.github.claudecode.ccgui.config.CCGuiConfig
import com.github.claudecode.ccgui.util.logger
import com.github.claudecode.ccgui.MyBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.io.File
import java.net.URLDecoder
import javax.swing.JPanel

/**
 * CCGUI 工具窗口工厂
 *
 * 创建并管理 CC Assistant 插件的工具窗口
 */
class MyToolWindowFactory : ToolWindowFactory, Disposable {

    private val log = logger<MyToolWindowFactory>()
    private var cefPanel: CefBrowserPanel? = null
    private var httpServer: FrontendHttpServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("[MyToolWindowFactory] Creating CC Assistant tool window for project: ${project.name}")

        try {
            // 获取配置中的工具窗口位置并设置 anchor
            val config = CCGuiConfig.getInstance(project).getConfig()
            val anchor = parseAnchor(config.toolWindowAnchor)
            toolWindow.setAnchor(anchor, null)

            // 设置工具窗口标题
            val toolWindowTitle = MyBundle.message("tool.window.title")
            toolWindow.setTitle(toolWindowTitle)
            // 设置左侧悬浮图标悬停时显示的名称
            toolWindow.setStripeTitle(toolWindowTitle)

            // 创建 CefBrowserPanel
            cefPanel = CefBrowserPanel(project)
            val browser = cefPanel!!.init()

            log.info("[MyToolWindowFactory] Browser initialized: ${browser.component != null}")

            // 创建使用 BorderLayout 的面板，支持动态拉伸
            val panel = JPanel(BorderLayout()).apply {
                add(browser.component, BorderLayout.CENTER)
                // 设置合适的边框间距
                border = null
            }

            // 强制组件可见性
            browser.component.isVisible = true
            panel.isVisible = true

            log.info("[MyToolWindowFactory] Panel created, component visible: ${browser.component.isVisible}, panel visible: ${panel.isVisible}")

            // 注入 CefBrowserPanel 引用到 StreamingOutputEngine（用于 Java→JS 事件推送）
            StreamingOutputEngine.getInstance(project).setCefPanel(cefPanel!!)

            // 添加到工具窗口
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)

            // 加载前端页面（后台执行，避免阻塞 EDT）
            scope.launch(Dispatchers.IO) {
                // 等待组件可显示后再加载页面
                delay(500) // 给 EDT 时间完成组件布局
                loadFrontendPage(project, toolWindow)
            }

            log.info("[MyToolWindowFactory] CC Assistant tool window created successfully with anchor: ${config.toolWindowAnchor}")
        } catch (e: Exception) {
            log.error("[MyToolWindowFactory] Failed to create tool window content", e)
            // 降级处理：显示错误面板
            val panel = JPanel()
            panel.add(javax.swing.JLabel("${MyBundle.message("tool.window.init.failed")}: ${e.message}"))
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
        }
    }

    /**
     * 解析 anchor 配置字符串
     */
    private fun parseAnchor(anchorStr: String): ToolWindowAnchor {
        return when (anchorStr.lowercase()) {
            "left" -> ToolWindowAnchor.LEFT
            "right" -> ToolWindowAnchor.RIGHT
            "bottom" -> ToolWindowAnchor.BOTTOM
            else -> ToolWindowAnchor.RIGHT  // 默认右侧
        }
    }

    /**
     * 加载前端页面（后台执行）
     */
    private suspend fun loadFrontendPage(project: Project, toolWindow: ToolWindow) {
        withContext(Dispatchers.IO) {
            try {
                // 优先加载本地开发服务器
                val devServerUrl = "http://localhost:3000"
                val useDevServer = isDevServerAvailable(devServerUrl)

                // 切换到主线程执行 UI 操作
                withContext(Dispatchers.Main) {
                    if (useDevServer) {
                        log.info("[MyToolWindowFactory] Using development server: $devServerUrl")
                        cefPanel?.loadHtmlPage(devServerUrl)
                    } else {
                        // 启动内嵌 HTTP 服务器并加载前端资源
                        loadProductionFrontend()
                    }
                }
            } catch (e: Exception) {
                log.error("[MyToolWindowFactory] Failed to load frontend", e)
            }
        }
    }

    /**
     * 加载生产环境前端（后台执行）
     *
     * 策略：
     * 1. 从插件 JAR 中提取 webview 目录到临时目录
     * 2. 启动内嵌 HTTP 服务器提供静态文件
     * 3. 让 JCEF 加载 http://localhost:PORT/index.html
     *
     * 使用 HTTP 服务器而非 file:// URL，解决 JCEF 沙盒限制问题
     */
    private suspend fun loadProductionFrontend() {
        // 在 IO 线程执行提取和服务器启动
        withContext(Dispatchers.IO) {
            // 1. 提取前端资源到临时目录
            val tempDir = extractFrontendToTemp()
            if (tempDir == null) {
                log.error("[MyToolWindowFactory] Failed to extract frontend to temp directory")
                return@withContext
            }

            // 2. 启动 HTTP 服务器
            httpServer = FrontendHttpServer(tempDir)
            val httpUrl = httpServer!!.start()
            log.info("[MyToolWindowFactory] HTTP server started at: $httpUrl")

            // 3. 切换到主线程加载页面
            withContext(Dispatchers.Main) {
                log.info("[MyToolWindowFactory] Loading frontend from HTTP server: $httpUrl")
                cefPanel?.loadHtmlPage(httpUrl)
            }
        }
    }

    /**
     * 从 JAR 提取前端资源到临时目录
     * @return 临时目录的 File 对象，或 null 如果失败
     */
    private fun extractFrontendToTemp(): File? {
        try {
            log.info("[MyToolWindowFactory] Starting frontend extraction")

            // 1. 使用插件类加载器获取资源（不是系统类加载器）
            // IDEA 插件运行在沙盒中，资源在 PluginClassLoader 中，不在 SystemClassLoader 中
            val classLoader = this::class.java.classLoader
            val distUrl = classLoader.getResource("webview/dist/index.html")
            if (distUrl == null) {
                log.error("[MyToolWindowFactory] Frontend resource not found in classpath")
                return null
            }
            log.info("[MyToolWindowFactory] Found frontend resource at: $distUrl")

            // 2. 解析 jar:file: URL 获取 JAR 路径和内部资源路径
            // jar:file:/path/to/plugin.jar!/webview/dist/index.html
            val jarUrlStr = distUrl.toString()
            if (!jarUrlStr.startsWith("jar:file:")) {
                log.error("[MyToolWindowFactory] Unexpected URL scheme: $jarUrlStr")
                return null
            }

            val bangIndex = jarUrlStr.indexOf("!/")
            if (bangIndex == -1) {
                log.error("[MyToolWindowFactory] Failed to parse JAR URL: $jarUrlStr")
                return null
            }

            // 提取并解码 JAR 文件路径（处理 URL 编码的空格等）
            val encodedJarPath = jarUrlStr.substring("jar:file:".length, bangIndex)
            val jarPath = URLDecoder.decode(encodedJarPath, "UTF-8")
            val innerBasePath = jarUrlStr.substring(bangIndex + 2) // e.g., "webview/dist/index.html"
            val webappRootDir = innerBasePath.substringBeforeLast("/") // "webview"
            log.info("[MyToolWindowFactory] Extracting from JAR: $jarPath, inner path: $webappRootDir")

            // 3. 使用标准 Java API 创建临时目录（避免 IntelliJ 沙盒限制）
            val tempDir = java.nio.file.Files.createTempDirectory("ccgui-webview").toFile()
            log.info("[MyToolWindowFactory] Temp directory: ${tempDir.absolutePath}")

            // 4. 读取 JAR 并提取 webview 目录下的所有文件
            val jarFile = java.util.jar.JarFile(jarPath)
            var fileCount = 0
            try {
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryPath = entry.name
                    // 只提取 webview/ 目录下的资源（排除 META-INF 等）
                    if (entryPath.startsWith("$webappRootDir/") && !entry.isDirectory) {
                        val relativePath = entryPath.substring((webappRootDir + "/").length)
                        val outFile = File(tempDir, relativePath)
                        outFile.parentFile?.mkdirs()
                        jarFile.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        fileCount++
                    }
                }
                log.info("[MyToolWindowFactory] Extracted $fileCount files to temp directory")
            } finally {
                jarFile.close()
            }

            // 5. 验证 index.html 存在
            val indexFile = File(tempDir, "index.html")
            if (indexFile.exists()) {
                log.info("[MyToolWindowFactory] Frontend extraction complete, index.html found")
                return tempDir
            } else {
                log.error("[MyToolWindowFactory] index.html not found after extraction: ${indexFile.absolutePath}")
                return null
            }
        } catch (e: Exception) {
            log.error("[MyToolWindowFactory] Failed to extract frontend", e)
            return null
        }
    }

    /**
     * 检查开发服务器是否可用（后台执行）
     */
    private suspend fun isDevServerAvailable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            log.info("[MyToolWindowFactory] Checking dev server availability: $url")
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.connect()
            log.info("[MyToolWindowFactory] Dev server is available: $url")
            true
        } catch (e: Exception) {
            log.info("[MyToolWindowFactory] Dev server not available: $url - ${e.message}")
            false
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    override fun dispose() {
        scope.cancel()
        httpServer?.stop()
        httpServer?.dispose()
        httpServer = null
        cefPanel?.dispose()
        cefPanel = null
        log.info("[MyToolWindowFactory] Disposed")
    }
}
