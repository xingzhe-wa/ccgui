package com.github.claudecode.ccgui.application.hook

import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * 钩子管理器
 *
 * 管理工具执行前后的钩子链，支持 PreToolUse、PostToolUse、Notification、Stop 等钩子类型
 */
class HookManager(private val project: Project) {

    private val log = logger<HookManager>()

    /** 钩子定义映射 */
    private val hooks = ConcurrentHashMap<HookType, MutableList<HookDefinition>>()

    /** 钩子链执行器 */
    private val hookChains = ConcurrentHashMap<HookType, HookChain>()

    /** 钩子优先级索引（按优先级排序的钩子 ID 列表） */
    private val priorityIndex = ConcurrentHashMap<HookType, List<String>>()

    /** 是否启用并行执行（默认关闭，钩子串行执行） */
    @Volatile
    private var parallelExecution = false

    init {
        log.info("HookManager initialized")
    }

    /**
     * 钩子类型
     */
    enum class HookType {
        /** 工具执行前 */
        PRE_TOOL_USE,
        /** 工具执行后 */
        POST_TOOL_USE,
        /** 通知事件 */
        NOTIFICATION,
        /** 会话停止 */
        STOP,
        /** 会话开始 */
        SESSION_START,
        /** 会话结束 */
        SESSION_END,
        /** 消息发送前 */
        PRE_MESSAGE_SEND,
        /** 消息接收后 */
        POST_MESSAGE_RECEIVE
    }

    /**
     * 钩子定义
     */
    data class HookDefinition(
        val id: String,
        val type: HookType,
        val name: String,
        val description: String,
        val matcher: HookMatcher,
        val handler: HookHandler,
        val priority: Int = 0,
        val enabled: Boolean = true
    )

    /**
     * 钩子匹配器
     */
    interface HookMatcher {
        fun matches(context: HookContext): Boolean
    }

    /**
     * 简单匹配器（基于工具名称模式）
     */
    class ToolNameMatcher(private val pattern: String) : HookMatcher {
        override fun matches(context: HookContext): Boolean {
            return context.toolName?.matches(pattern.toRegex()) == true
        }
    }

    /**
     * 复合匹配器
     */
    class CompositeMatcher(private val matchers: List<HookMatcher>, private val combineType: CombineType = CombineType.AND) : HookMatcher {
        enum class CombineType { AND, OR }
        override fun matches(context: HookContext): Boolean {
            return when (combineType) {
                CombineType.AND -> matchers.all { it.matches(context) }
                CombineType.OR -> matchers.any { it.matches(context) }
            }
        }
    }

    /**
     * 钩子处理器
     */
    interface HookHandler {
        suspend fun handle(context: HookContext): HookResult
    }

    /**
     * 简单处理器（执行给定函数）
     */
    class SimpleHandler(private val fn: suspend (HookContext) -> HookResult) : HookHandler {
        override suspend fun handle(context: HookContext): HookResult = fn(context)
    }

    /**
     * 钩子上下文
     */
    data class HookContext(
        val hookType: HookType,
        val toolName: String? = null,
        val toolInput: Any? = null,
        val toolOutput: Any? = null,
        val sessionId: String? = null,
        val projectId: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    ) {
        fun withMetadata(vararg pairs: Pair<String, Any>): HookContext {
            return copy(metadata = metadata + pairs)
        }
    }

    /**
     * 钩子执行结果
     */
    sealed class HookResult {
        /** 继续执行，不拦截 */
        object Continue : HookResult()

        /** 阻止操作 */
        data class Block(val reason: String) : HookResult()

        /** 修改后的上下文 */
        data class Modified(val context: HookContext, val proceed: Boolean = true) : HookResult()

        /** 错误 */
        data class Error(val message: String, val throwable: Throwable? = null) : HookResult()
    }

    /**
     * 钩子链
     */
    inner class HookChain(private val type: HookType) {
        private val chain = mutableListOf<HookDefinition>()

        fun addHook(hook: HookDefinition) {
            chain.add(hook)
            chain.sortByDescending { it.priority }
        }

        fun removeHook(hookId: String) {
            chain.removeAll { it.id == hookId }
        }

        suspend fun execute(context: HookContext): HookResult {
            var currentContext = context

            for (hook in chain) {
                if (!hook.enabled) continue
                if (!hook.matcher.matches(currentContext)) continue

                log.debug("Executing hook: ${hook.name} for ${hook.type}")

                val result = try {
                    hook.handler.handle(currentContext)
                } catch (e: Exception) {
                    log.error("Hook execution failed: ${hook.name}", e)
                    HookResult.Error(e.message ?: "Hook execution failed", e)
                }

                when (result) {
                    is HookResult.Continue -> { /* 继续下一个钩子 */ }
                    is HookResult.Block -> {
                        log.info("Hook blocked: ${hook.name}, reason: ${result.reason}")
                        return result
                    }
                    is HookResult.Modified -> {
                        currentContext = result.context
                        if (!result.proceed) {
                            log.info("Hook modified context and stopped: ${hook.name}")
                            return result
                        }
                    }
                    is HookResult.Error -> {
                        log.error("Hook error: ${hook.name}: ${result.message}")
                        return result
                    }
                }
            }

            return HookResult.Continue
        }

        fun clear() = chain.clear()
    }

    /**
     * 注册钩子
     */
    fun registerHook(hook: HookDefinition) {
        hooks.getOrPut(hook.type) { mutableListOf() }.add(hook)

        hookChains.getOrPut(hook.type) {
            HookChain(hook.type)
        }.addHook(hook)

        // 更新优先级索引
        updatePriorityIndex(hook.type)

        log.debug("Registered hook: ${hook.name} for type: ${hook.type}")
    }

    /**
     * 更新优先级索引
     */
    private fun updatePriorityIndex(type: HookType) {
        val hooksList = hooks[type] ?: return
        priorityIndex[type] = hooksList
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .map { it.id }
    }

    /**
     * 批量注册钩子
     */
    fun registerHooks(hooks: List<HookDefinition>) {
        hooks.forEach { registerHook(it) }
    }

    /**
     * 创建并注册钩子（便捷方法）
     */
    fun createAndRegister(
        id: String,
        type: HookType,
        name: String,
        description: String,
        toolPattern: String? = null,
        priority: Int = 0,
        handler: suspend (HookContext) -> HookResult
    ) {
        val matcher = toolPattern?.let { ToolNameMatcher(it) }
            ?: object : HookMatcher { override fun matches(context: HookContext) = true }

        registerHook(
            HookDefinition(
                id = id,
                type = type,
                name = name,
                description = description,
                matcher = matcher,
                handler = SimpleHandler(handler),
                priority = priority
            )
        )
    }

    /**
     * 执行特定类型的钩子链
     */
    suspend fun executeHooks(type: HookType, context: HookContext): HookResult {
        val chain = hookChains[type]
        if (chain == null || chain.equals(Unit)) {
            return HookResult.Continue
        }

        return chain.execute(context)
    }

    /**
     * 便捷方法：执行 PreToolUse 钩子
     */
    suspend fun preToolUse(toolName: String, input: Any, sessionId: String? = null): HookResult {
        val context = HookContext(
            hookType = HookType.PRE_TOOL_USE,
            toolName = toolName,
            toolInput = input,
            sessionId = sessionId
        )
        return executeHooks(HookType.PRE_TOOL_USE, context)
    }

    /**
     * 便捷方法：执行 PostToolUse 钩子
     */
    suspend fun postToolUse(
        toolName: String,
        input: Any,
        output: Any,
        sessionId: String? = null
    ): HookResult {
        val context = HookContext(
            hookType = HookType.POST_TOOL_USE,
            toolName = toolName,
            toolInput = input,
            toolOutput = output,
            sessionId = sessionId
        )
        return executeHooks(HookType.POST_TOOL_USE, context)
    }

    /**
     * 获取特定类型的所有钩子
     */
    fun getHooks(type: HookType): List<HookDefinition> {
        return hooks[type]?.toList() ?: emptyList()
    }

    /**
     * 获取所有钩子
     */
    fun getAllHooks(): Map<HookType, List<HookDefinition>> {
        return hooks.mapValues { it.value.toList() }
    }

    /**
     * 启用/禁用钩子
     */
    fun setHookEnabled(hookId: String, enabled: Boolean) {
        hooks.values.forEach { hooksList ->
            hooksList.find { it.id == hookId }?.let { hook ->
                hooksList[hooksList.indexOf(hook)] = hook.copy(enabled = enabled)
            }
        }
    }

    /**
     * 移除钩子
     */
    fun unregisterHook(hookId: String) {
        hooks.values.forEach { hooksList ->
            hooksList.find { it.id == hookId }?.let { hook ->
                hooksList.remove(hook)
                hookChains[hook.type]?.removeHook(hookId)
            }
        }
    }

    /**
     * 清空特定类型的钩子
     */
    fun clearHooks(type: HookType) {
        hooks[type]?.clear()
        hookChains[type]?.clear()
    }

    /**
     * 清空所有钩子
     */
    fun clearAllHooks() {
        hooks.clear()
        hookChains.clear()
        priorityIndex.clear()
    }

    /**
     * 设置是否启用并行执行
     *
     * @param enable true 启用并行执行，false 禁用（默认）
     */
    fun setParallelExecution(enable: Boolean) {
        parallelExecution = enable
        log.info("Hook parallel execution: ${if (enable) "enabled" else "disabled"}")
    }

    /**
     * 获取钩子执行统计信息
     *
     * @return 统计信息 Map
     */
    fun getExecutionStats(): Map<String, Any> {
        return mapOf(
            "totalHooks" to hooks.values.sumOf { it.size },
            "hooksByType" to hooks.mapValues { it.value.size },
            "enabledHooks" to hooks.values.flatten().count { it.enabled },
            "parallelExecution" to parallelExecution
        )
    }

    companion object {
        fun getInstance(project: Project): HookManager =
            project.getService(HookManager::class.java)
    }
}
