package com.github.claudecode.ccgui.action

import com.github.claudecode.ccgui.bridge.BridgeManager
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 代码Bug分析操作
 *
 * 在编辑器中选中代码后，右键菜单 → "分析Bug" → 打开 CC Assistant 工具窗口并发送Bug分析请求
 */
class CodeBugAction : AnAction() {

    private val log = logger<CodeBugAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText?.trim() ?: run {
            log.warn("No text selected for bug analysis")
            return
        }

        if (selectedText.isEmpty()) {
            log.warn("Empty text selected for bug analysis")
            return
        }

        openToolWindow(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val bridgeManager = BridgeManager.getInstance(project)
                val sessionId = getCurrentSessionId(project)
                bridgeManager.sendMessage(
                    message = "/bug\n$selectedText",
                    sessionId = sessionId,
                    callback = object : com.github.claudecode.ccgui.bridge.SimpleStreamCallback() {
                        override fun onStreamStart() {
                            log.info("Bug analysis request sent")
                        }

                        override fun onStreamError(error: String) {
                            log.warn("Bug analysis failed: $error")
                        }
                    }
                )
            } catch (ex: Exception) {
                log.error("Failed to send bug analysis request", ex)
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
            log.warn("CC Assistant tool window not found")
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
            val sessionManager = com.github.claudecode.ccgui.application.session.SessionService.getInstance(project)
            sessionManager.getCurrentSession()?.id ?: ""
        } catch (e: Exception) {
            log.warn("Failed to get current session ID: ${e.message}")
            ""
        }
    }
}