package com.github.xingzhewa.ccgui.action

import com.github.xingzhewa.ccgui.bridge.BridgeManager
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 代码优化操作
 *
 * 在编辑器中选中代码后，右键菜单 → "优化代码" → 打开 CCGUI 工具窗口并发送优化请求
 */
class CodeOptimizeAction : AnAction() {

    private val log = logger<CodeOptimizeAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText?.trim() ?: run {
            log.warn("No text selected for code optimization")
            return
        }

        if (selectedText.isEmpty()) {
            log.warn("Empty text selected for code optimization")
            return
        }

        openToolWindow(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val bridgeManager = BridgeManager.getInstance(project)
                val sessionId = getCurrentSessionId(project)
                bridgeManager.sendMessage(
                    message = "/optimize\n$selectedText",
                    sessionId = sessionId,
                    callback = object : com.github.xingzhewa.ccgui.bridge.SimpleStreamCallback() {
                        override fun onStreamStart() {
                            log.info("Code optimization request sent")
                        }

                        override fun onStreamError(error: String) {
                            log.warn("Code optimization failed: $error")
                        }
                    }
                )
            } catch (ex: Exception) {
                log.error("Failed to send code optimization request", ex)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectionModel = editor?.selectionModel

        e.presentation.isEnabledAndVisible = project != null &&
                editor != null &&
                selectionModel != null &&
                !selectionModel.selectedText.isNullOrBlank()
    }

    private fun openToolWindow(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("CCGUI") ?: run {
            log.warn("CCGUI tool window not found")
            return
        }

        if (toolWindow.isVisible) {
            toolWindow.activate(null)
        } else {
            toolWindow.show(null)
        }
    }

    private fun getCurrentSessionId(project: Project): String {
        return try {
            val sessionManager = com.github.xingzhewa.ccgui.application.session.SessionManager.getInstance(project)
            sessionManager.getCurrentSession()?.id ?: ""
        } catch (e: Exception) {
            log.warn("Failed to get current session ID: ${e.message}")
            ""
        }
    }
}
