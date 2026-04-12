package com.github.claudecode.ccgui.startup

import com.github.claudecode.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.claudecode.ccgui.adaptation.sdk.SdkPermissionHandler
import com.github.claudecode.ccgui.application.interaction.InteractiveRequestEngine
import com.github.claudecode.ccgui.application.session.SessionService
import com.github.claudecode.ccgui.browser.CefBrowserPanel
import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.InteractiveQuestionEvent
import com.github.claudecode.ccgui.infrastructure.eventbus.PermissionRequestEvent
import com.github.claudecode.ccgui.infrastructure.eventbus.QuestionAnsweredEvent
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MyProjectActivity : ProjectActivity, Disposable {
    private val log = logger<MyProjectActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingPermissions = ConcurrentHashMap<String, java.util.concurrent.CountDownLatch>()
    private var currentProject: Project? = null

    /** EventBus 订阅 ID，用于取消订阅 */
    private var permissionSubscriptionId: String? = null
    private var questionSubscriptionId: String? = null

    override suspend fun execute(project: Project) {
        currentProject = project
        log.info("CCGUI starting for project: ${project.name}")

        // 初始化 EventBus（建立 MessageBus 连接）
        EventBus.init(project)

        // Initialize default session if none exists
        val sessionManager = SessionService.getInstance(project)
        if (sessionManager.getSessionCount() == 0) {
            sessionManager.createSession("Default Session", com.github.claudecode.ccgui.model.session.SessionType.PROJECT)
            log.info("Created default session")
        }

        // Check CLI availability（使用统一的 CLI 检测方法）
        val claudeClient = ClaudeCodeClient.getInstance(project)
        if (claudeClient.isCliAvailable()) {
            val version = claudeClient.getCliVersion() ?: "unknown"
            log.info("Claude CLI available: $version")
        } else {
            log.warn("Claude CLI not found. Please install Claude Code CLI from https://docs.anthropic.com/en/docs/claude-code/overview")
        }

        // 订阅权限请求事件，将权限请求路由到 InteractiveRequestEngine 显示给用户
        permissionSubscriptionId = EventBus.subscribe(project, PermissionRequestEvent::class.java) { event ->
            scope.launch {
                handlePermissionRequest(event)
            }
        }

        // 订阅交互式问题事件，推送到 JavaScript 前端显示交互面板
        questionSubscriptionId = EventBus.subscribe(project, InteractiveQuestionEvent::class.java) { event ->
            scope.launch {
                pushQuestionToJavaScript(event.question)
            }
        }
    }

    private suspend fun handlePermissionRequest(event: PermissionRequestEvent) {
        val project = currentProject ?: return
        val engine = InteractiveRequestEngine.getInstance(project)
        // 权限场景不推送到 JS，传递 null 回调
        val answer = engine.askQuestion(event.question, onQuestionAsked = null)
        // 将用户回答路由回 SdkPermissionHandler
        val decision = when (answer) {
            is InteractiveRequestEngine.QuestionAnswer.Confirmation -> {
                if (answer.allowed) SdkPermissionHandler.Decision.ALLOW else SdkPermissionHandler.Decision.DENY
            }
            else -> SdkPermissionHandler.Decision.DENY
        }
        SdkPermissionHandler.getInstance(project).submitDecision(event.requestId, decision)
    }

    /**
     * 将交互式问题推送到 JavaScript 前端
     */
    private fun pushQuestionToJavaScript(question: com.github.claudecode.ccgui.model.interaction.InteractiveQuestion) {
        val project = currentProject ?: return
        // 查找 CefBrowserPanel 实例（通过 ToolWindow）
        try {
            val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("CCGUI")
            val cefPanel = toolWindow?.component?.getComponent(0)
            if (cefPanel is CefBrowserPanel) {
                cefPanel.sendToJavaScript("streaming:question", mapOf(
                    "questionId" to question.questionId,
                    "questionType" to question.questionType.name,
                    "message" to question.question,
                    "options" to question.options.map { opt ->
                        mapOf(
                            "id" to opt.id,
                            "label" to opt.label,
                            "description" to (opt.description ?: "")
                        )
                    },
                    "required" to question.required
                ))
            } else {
                log.warn("CefBrowserPanel not found for question push")
            }
        } catch (e: Exception) {
            log.warn("Failed to push question to JavaScript: ${e.message}")
        }
    }

    override fun dispose() {
        val project = currentProject
        // 取消 EventBus 订阅，防止内存泄漏
        permissionSubscriptionId?.let { project?.let { p -> EventBus.unsubscribe(p, it) } }
        questionSubscriptionId?.let { project?.let { p -> EventBus.unsubscribe(p, it) } }
        scope.cancel()
    }
}
