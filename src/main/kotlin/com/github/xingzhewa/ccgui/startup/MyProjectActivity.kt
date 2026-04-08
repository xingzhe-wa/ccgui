package com.github.xingzhewa.ccgui.startup

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkPermissionHandler
import com.github.xingzhewa.ccgui.application.interaction.InteractiveRequestEngine
import com.github.xingzhewa.ccgui.application.session.SessionManager
import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.PermissionRequestEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.QuestionAnsweredEvent
import com.github.xingzhewa.ccgui.util.logger
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingPermissions = ConcurrentHashMap<String, java.util.concurrent.CountDownLatch>()
    private var currentProject: Project? = null

    override suspend fun execute(project: Project) {
        currentProject = project
        log.info("CCGUI starting for project: ${project.name}")

        // Initialize default session if none exists
        val sessionManager = SessionManager.getInstance(project)
        if (sessionManager.getSessionCount() == 0) {
            sessionManager.createSession("Default Session", com.github.xingzhewa.ccgui.model.session.SessionType.PROJECT)
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
        EventBus.subscribeType(PermissionRequestEvent::class.java) { event ->
            scope.launch {
                handlePermissionRequest(event)
            }
        }
    }

    private suspend fun handlePermissionRequest(event: PermissionRequestEvent) {
        val project = currentProject ?: return
        val engine = InteractiveRequestEngine.getInstance(project)
        val answer = engine.askQuestion(event.question)
        // 将用户回答路由回 SdkPermissionHandler
        val decision = when (answer) {
            is InteractiveRequestEngine.QuestionAnswer.Confirmation -> {
                if (answer.allowed) SdkPermissionHandler.Decision.ALLOW else SdkPermissionHandler.Decision.DENY
            }
            else -> SdkPermissionHandler.Decision.DENY
        }
        SdkPermissionHandler.getInstance(project).submitDecision(event.requestId, decision)
    }

    override fun dispose() {
        scope.cancel()
    }
}
