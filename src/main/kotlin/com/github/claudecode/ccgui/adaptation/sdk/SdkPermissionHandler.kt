package com.github.claudecode.ccgui.adaptation.sdk

import com.github.claudecode.ccgui.infrastructure.eventbus.EventBus
import com.github.claudecode.ccgui.infrastructure.eventbus.PermissionRequestEvent
import com.github.claudecode.ccgui.model.interaction.InteractiveQuestion
import com.github.claudecode.ccgui.model.interaction.QuestionOption
import com.github.claudecode.ccgui.model.interaction.QuestionType
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * SDK 权限处理器
 *
 * 处理 Claude Code SDK 执行工具调用时的权限请求。
 * SDK 通过 --permission-prompt-tool 发出权限请求，
 * 本处理器将请求展示给用户并接收决策。
 */
@Service(Service.Level.PROJECT)
class SdkPermissionHandler(private val project: Project) {

    private val log = logger<SdkPermissionHandler>()

    /** 安全工具列表（自动允许） */
    private val safeTools = setOf("Read", "Glob", "Grep", "LS", "LSFiles")

    /** 权限策略 */
    enum class PermissionPolicy {
        ALWAYS_ASK,
        AUTO_ALLOW_SAFE,
        AUTO_ALLOW_ALL
    }

    private val _policy = MutableStateFlow(PermissionPolicy.AUTO_ALLOW_SAFE)
    val policy: StateFlow<PermissionPolicy> = _policy.asStateFlow()

    /** 已缓存的权限决策: toolName → allowed */
    private val cachedDecisions = ConcurrentHashMap<String, Boolean>()

    /** 待处理的权限请求: requestId → (toolName, result holder) */
    private val pendingRequests = ConcurrentHashMap<String, PermissionResult>()

    data class PermissionResult(
        val toolName: String,
        val description: String,
        var decision: Decision? = null
    )

    enum class Decision { ALLOW, DENY, ALWAYS_ALLOW }

    /**
     * 处理权限请求
     *
     * @param toolName 工具名称（如 "Bash", "Write", "Edit"）
     * @param input 工具输入参数
     * @return true=允许, false=拒绝
     */
    fun handlePermissionRequest(
        toolName: String,
        input: Map<String, Any>
    ): Boolean {
        // 1. 检查策略
        if (_policy.value == PermissionPolicy.AUTO_ALLOW_ALL) {
            log.info("Auto-allowed (policy=ALL): $toolName")
            return true
        }

        if (_policy.value == PermissionPolicy.AUTO_ALLOW_SAFE && toolName in safeTools) {
            log.info("Auto-allowed (safe tool): $toolName")
            return true
        }

        // 2. 检查缓存决策
        cachedDecisions[toolName]?.let {
            log.info("Cached decision for $toolName: $it")
            return it
        }

        // 3. 向用户展示权限请求
        val description = buildPermissionDescription(toolName, input)
        val requestId = "perm_${toolName}_${System.currentTimeMillis()}"

        val result = PermissionResult(toolName, description)
        pendingRequests[requestId] = result

        // 通过EventBus通知UI展示权限对话框
        val question = InteractiveQuestion.confirmation(
            question = description
        )
        EventBus.publish(PermissionRequestEvent(
            project = project,
            requestId = requestId,
            toolName = toolName,
            description = description,
            question = question
        ))

        log.info("Permission request for $toolName: showing to user (requestId=$requestId)")

        // 在同步上下文中返回false（异步模式下由用户决策后回调）
        return false
    }

    /**
     * 提交权限决策
     *
     * 由UI层调用，回应用户的权限选择
     */
    fun submitDecision(requestId: String, decision: Decision) {
        val result = pendingRequests.remove(requestId) ?: run {
            log.warn("No pending request for requestId: $requestId")
            return
        }

        result.decision = decision

        when (decision) {
            Decision.ALLOW -> {
                log.info("Permission allowed: ${result.toolName}")
            }
            Decision.DENY -> {
                log.info("Permission denied: ${result.toolName}")
            }
            Decision.ALWAYS_ALLOW -> {
                cachedDecisions[result.toolName] = true
                log.info("Permission cached as allowed: ${result.toolName}")
            }
        }
    }

    /**
     * 设置权限策略
     */
    fun setPolicy(policy: PermissionPolicy) {
        _policy.value = policy
        log.info("Permission policy set to: $policy")
    }

    /**
     * 清除缓存的权限决策
     */
    fun clearCachedDecisions() {
        cachedDecisions.clear()
    }

    private fun buildPermissionDescription(toolName: String, input: Map<String, Any>): String {
        return when (toolName) {
            "Bash" -> "Execute command: `${input["command"]}`"
            "Write" -> "Write file: ${input["file_path"]}"
            "Edit" -> "Edit file: ${input["file_path"]}"
            "MultiEdit" -> "Edit multiple files"
            else -> "Use tool: $toolName"
        }
    }

    companion object {
        fun getInstance(project: Project): SdkPermissionHandler =
            project.getService(SdkPermissionHandler::class.java)
    }
}
