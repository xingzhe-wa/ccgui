package com.github.claudecode.ccgui.toolWindow

import com.github.claudecode.ccgui.browser.CefBrowserPanel
import com.github.claudecode.ccgui.application.streaming.StreamingOutputEngine
import com.github.claudecode.ccgui.config.CCGuiConfig
import com.github.claudecode.ccgui.util.HtmlLoader
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
import javax.swing.JPanel

/**
 * CCGUI 工具窗口工厂
 *
 * 创建并管理 CC Assistant 插件的工具窗口
 *
 * 前端加载策略：
 * - 生产环境：使用 vite-plugin-singlefile 构建的单文件 HTML，通过 loadHTML() 直接加载
 * - 开发环境：检测 localhost:3000，如果可用则使用 loadURL() 加载开发服务器
 */
class MyToolWindowFactory : ToolWindowFactory, Disposable {

    private val log = logger<MyToolWindowFactory>()
    private var cefPanel: CefBrowserPanel? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val htmlLoader = HtmlLoader(MyToolWindowFactory::class.java)

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
            toolWindow.setStripeTitle(toolWindowTitle)

            // 创建 CefBrowserPanel
            cefPanel = CefBrowserPanel(project)
            val browser = cefPanel!!.init()

            log.info("[MyToolWindowFactory] Browser initialized: ${browser.component != null}")

            // 创建使用 BorderLayout 的面板，支持动态拉伸
            val panel = JPanel(BorderLayout()).apply {
                add(browser.component, BorderLayout.CENTER)
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
                loadFrontendPage()
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
     *
     * 策略：
     * 1. 检查开发服务器是否可用（localhost:3000）
     * 2. 如果可用，使用 loadURL() 加载开发服务器（支持热重载）
     * 3. 如果不可用，使用 loadHTML() 加载生产构建的单文件 HTML
     */
    private suspend fun loadFrontendPage() {
        withContext(Dispatchers.IO) {
            try {
                // 检查开发服务器是否可用
                val devServerUrl = "http://localhost:3000"
                val useDevServer = isDevServerAvailable(devServerUrl)

                // 切换到主线程执行 UI 操作
                withContext(Dispatchers.Main) {
                    if (useDevServer) {
                        log.info("[MyToolWindowFactory] Using development server: $devServerUrl")
                        cefPanel?.loadHtmlPage(devServerUrl)
                    } else {
                        // 加载生产构建的单文件 HTML
                        log.info("[MyToolWindowFactory] Loading production HTML from classpath")
                        val htmlContent = htmlLoader.loadChatHtml()
                        cefPanel?.loadHtmlContent(htmlContent)
                    }
                }
            } catch (e: Exception) {
                log.error("[MyToolWindowFactory] Failed to load frontend", e)
            }
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
        cefPanel?.dispose()
        cefPanel = null
        log.info("[MyToolWindowFactory] Disposed")
    }
}
