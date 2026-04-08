package com.github.xingzhewa.ccgui.toolWindow

import com.github.xingzhewa.ccgui.browser.CefBrowserPanel
import com.github.xingzhewa.ccgui.application.streaming.StreamingOutputEngine
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel

/**
 * CCGUI 工具窗口工厂
 *
 * 创建并管理 ClaudeCodeJet 插件的工具窗口
 */
class MyToolWindowFactory : ToolWindowFactory, Disposable {

    private val log = logger<MyToolWindowFactory>()
    private var cefPanel: CefBrowserPanel? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("Creating CCGUI tool window for project: ${project.name}")

        try {
            // 创建 CefBrowserPanel
            cefPanel = CefBrowserPanel(project)
            val browser = cefPanel!!.init()

            // 创建面板包装浏览器
            val panel = JPanel()
            panel.add(browser.component)

            // 注入 CefBrowserPanel 引用到 StreamingOutputEngine（用于 Java→JS 事件推送）
            StreamingOutputEngine.getInstance(project).setCefPanel(cefPanel!!)

            // 添加到工具窗口
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)

            // 加载前端页面
            loadFrontendPage(project, toolWindow)

            log.info("CCGUI tool window created successfully")
        } catch (e: Exception) {
            log.error("Failed to create tool window content", e)
            // 降级处理：显示错误面板
            val panel = JPanel()
            panel.add(javax.swing.JLabel("CCGUI 初始化失败: ${e.message}"))
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
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
     */
    private fun loadProductionFrontend() {
        try {
            // 从 resources 目录加载
            val resourcePath = "/webview/dist/index.html"
            val inputStream = java.lang.ClassLoader.getSystemResourceAsStream(resourcePath)
                ?: java.lang.ClassLoader.getSystemResourceAsStream("webview/dist/index.html")

            if (inputStream != null) {
                val htmlContent = inputStream.bufferedReader().use { it.readText() }
                cefPanel?.loadHtmlContent(htmlContent, "http://localhost/")
                log.info("Loaded production frontend from resources")
            } else {
                log.warn("Frontend resource not found: $resourcePath")
            }
        } catch (e: Exception) {
            log.error("Failed to load production frontend", e)
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
