package com.github.xingzhewa.ccgui.toolWindow

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.application.streaming.StreamingOutputEngine
import com.github.xingzhewa.ccgui.config.CCGuiConfig
import com.github.xingzhewa.ccgui.util.logger
import com.github.xingzhewa.ccgui.MyBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
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

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("Creating CC Assistant tool window for project: ${project.name}")

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

            // 创建使用 BorderLayout 的面板，支持动态拉伸
            val panel = JPanel(BorderLayout()).apply {
                add(browser.component, BorderLayout.CENTER)
                // 设置合适的边框间距
                border = null
            }

            // 注入 CefBrowserPanel 引用到 StreamingOutputEngine（用于 Java→JS 事件推送）
            StreamingOutputEngine.getInstance(project).setCefPanel(cefPanel!!)

            // 添加到工具窗口
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)

            // 加载前端页面
            loadFrontendPage(project, toolWindow)

            log.info("CC Assistant tool window created successfully with anchor: ${config.toolWindowAnchor}")
        } catch (e: Exception) {
            log.error("Failed to create tool window content", e)
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
     * 加载前端页面
     */
    private fun loadFrontendPage(project: Project, toolWindow: ToolWindow) {
        try {
            // 优先加载本地开发服务器
            val devServerUrl = "http://localhost:3000"
            val useDevServer = isDevServerAvailable(devServerUrl)

            if (useDevServer) {
                log.info("Using development server: $devServerUrl")
                cefPanel?.loadHtmlPage(devServerUrl)
            } else {
                // 加载打包的前端资源
                loadProductionFrontend()
            }
        } catch (e: Exception) {
            log.error("Failed to load frontend", e)
        }
    }

    /**
     * 加载生产环境前端
     *
     * 策略：从插件 JAR 中提取整个 webview 目录到临时目录，
     * 使用 file:// URL 加载，让相对路径的 /assets/... 正确解析
     */
    private fun loadProductionFrontend() {
        try {
            // 1. 使用插件类加载器获取资源（不是系统类加载器）
            // IDEA 插件运行在沙盒中，资源在 PluginClassLoader 中，不在 SystemClassLoader 中
            val classLoader = this::class.java.classLoader
            val distUrl = classLoader.getResource("webview/dist/index.html")
            if (distUrl == null) {
                log.error(MyBundle.message("frontend.resource.not.found"))
                return
            }
            log.info("Found frontend resource at: $distUrl")

            // 2. 解析 jar:file: URL 获取 JAR 路径和内部资源路径
            // jar:file:/path/to/plugin.jar!/webview/dist/index.html
            val jarUrlStr = distUrl.toString()
            if (!jarUrlStr.startsWith("jar:file:")) {
                log.error("${MyBundle.message("frontend.url.scheme.unexpected")}: $jarUrlStr")
                return
            }

            val bangIndex = jarUrlStr.indexOf("!/")
            if (bangIndex == -1) {
                log.error("${MyBundle.message("frontend.url.parse.failed")}: $jarUrlStr")
                return
            }

            // 提取并解码 JAR 文件路径（处理 URL 编码的空格等）
            val encodedJarPath = jarUrlStr.substring("jar:file:".length, bangIndex)
            val jarPath = URLDecoder.decode(encodedJarPath, "UTF-8")
            val innerBasePath = jarUrlStr.substring(bangIndex + 2) // e.g., "webview/dist/index.html"
            val webappRootDir = innerBasePath.substringBeforeLast("/") // "webview"
            log.info("${MyBundle.message("frontend.extracting")}: $jarPath, inner path: $webappRootDir")

            // 3. 使用 IntelliJ FileUtil 创建临时目录
            val tempDir = FileUtil.createTempDirectory("ccgui-webview", "", true)
            log.info("${MyBundle.message("frontend.extract.to.temp")}: ${tempDir.absolutePath}")

            // 4. 读取 JAR 并提取 webview 目录下的所有文件
            val jarFile = java.util.jar.JarFile(jarPath)
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
                    }
                }
            } finally {
                jarFile.close()
            }

            // 5. 使用 file:// URL 加载 HTML
            val indexFile = File(tempDir, "index.html")
            if (indexFile.exists()) {
                val fileUrl = indexFile.toURI().toURL().toString()
                log.info("${MyBundle.message("frontend.loading.from.file")}: $fileUrl")
                cefPanel?.loadHtmlPage(fileUrl)
            } else {
                log.error("index.html not found after extraction: ${indexFile.absolutePath}")
            }
        } catch (e: Exception) {
            log.error(MyBundle.message("frontend.load.failed"), e)
        }
    }

    /**
     * 检查开发服务器是否可用
     */
    private fun isDevServerAvailable(url: String): Boolean {
        return try {
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.connect()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    override fun dispose() {
        cefPanel?.dispose()
        cefPanel = null
    }
}
