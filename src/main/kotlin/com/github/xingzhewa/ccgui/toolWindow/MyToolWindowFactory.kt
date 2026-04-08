package com.github.xingzhewa.ccgui.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * CCGUI 工具窗口工厂
 *
 * 注意：当前为简化版本，仅用于验证编译。
 * 完整的前端加载功能需要在后端 Phase 1 实现 CefBrowserPanel 后完成。
 */
class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建简单的占位面板
        val panel = JPanel()
        panel.add(JLabel("CCGUI - 等待后端实现"))

        // 添加到工具窗口
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
