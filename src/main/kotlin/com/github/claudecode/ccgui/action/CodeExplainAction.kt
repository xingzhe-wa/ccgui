package com.github.claudecode.ccgui.action

import com.github.claudecode.ccgui.bridge.BridgeManager
import com.github.claudecode.ccgui.browser.CefBrowserPanel
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * 代码解释操作
 *
 * 在编辑器中选中代码后，右键菜单 → "解释代码" → 打开 CC Assistant 工具窗口并发送解释请求
 */
class CodeExplainAction : AnAction() {

    private val log = logger<CodeExplainAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText?.trim() ?: run {
            log.warn("No text selected for code explanation")
            return
        }

        if (selectedText.isEmpty()) {
            log.warn("Empty text selected for code explanation")
            return
        }

        // 打开工具窗口
        openToolWindow(project)

        // 异步发送解释请求
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val bridgeManager = BridgeManager.getInstance(project)
                val sessionId = getCurrentSessionId(project)
                bridgeManager.sendMessage(
                    message = "/explain\n$selectedText",
                    sessionId = sessionId,
                    callback = object : com.github.claudecode.ccgui.bridge.SimpleStreamCallback() {
                        override fun onStreamStart() {
                            log.info("Code explanation request sent successfully")
                        }

                        override fun onStreamError(error: String) {
                            log.warn("Code explanation request failed: $error")
                        }
                    }
                )
            } catch (ex: Exception) {
                log.error("Failed to send code explanation request", ex)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectionModel = editor?.selectionModel

        // 仅在有文本选中时启用
        e.presentation.isEnabledAndVisible = project != null &&
                editor != null &&
                selectionModel != null &&
                !selectionModel.selectedText.isNullOrBlank()
    }

    /**
     * 打开 CC Assistant 工具窗口
     */
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

    /**
     * 获取当前会话 ID
     */
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
