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
 * 代码测试生成操作
 *
 * 在编辑器中选中代码后，右键菜单 → "生成测试" → 打开 CC Assistant 工具窗口并发送测试生成请求
 */
class CodeTestAction : AnAction() {

    private val log = logger<CodeTestAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText?.trim() ?: run {
            log.warn("No text selected for test generation")
            return
        }

        if (selectedText.isEmpty()) {
            log.warn("Empty text selected for test generation")
            return
        }

        openToolWindow(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val bridgeManager = BridgeManager.getInstance(project)
                val sessionId = getCurrentSessionId(project)
                bridgeManager.sendMessage(
                    message = "/test\n$selectedText",
                    sessionId = sessionId,
                    callback = object : com.github.xingzhewa.ccgui.bridge.SimpleStreamCallback() {
                        override fun onStreamStart() {
                            log.info("Test generation request sent")
                        }

                        override fun onStreamError(error: String) {
                            log.warn("Test generation failed: $error")
                        }
                    }
                )
            } catch (ex: Exception) {
                log.error("Failed to send test generation request", ex)
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
            val sessionManager = com.github.xingzhewa.ccgui.application.session.SessionManager.getInstance(project)
            sessionManager.getCurrentSession()?.id ?: ""
        } catch (e: Exception) {
            log.warn("Failed to get current session ID: ${e.message}")
            ""
        }
    }
}
