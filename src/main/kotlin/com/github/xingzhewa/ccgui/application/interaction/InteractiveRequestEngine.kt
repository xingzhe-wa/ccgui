package com.github.xingzhewa.ccgui.application.interaction

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.InteractiveQuestionEvent
import com.github.xingzhewa.ccgui.infrastructure.eventbus.QuestionAnsweredEvent
import com.github.xingzhewa.ccgui.model.interaction.InteractiveQuestion
import com.github.xingzhewa.ccgui.model.interaction.QuestionOption
import com.github.xingzhewa.ccgui.model.interaction.QuestionType
import com.github.xingzhewa.ccgui.util.IdGenerator
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 交互式请求引擎
 *
 * 负责处理模型在执行过程中需要用户交互的场景
 *
 * 功能:
 * - 管理待回答的问题队列
 * - 处理问题超时
 * - 跟踪问题状态
 * - 提供问题回调机制
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class InteractiveRequestEngine(private val project: Project) : Disposable {

    private val log = logger<InteractiveRequestEngine>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 待回答的问题 */
    private val pendingQuestions = ConcurrentHashMap<String, PendingQuestion>()

    /** 问题计数器 */
    private val questionCounter = AtomicInteger(0)

    /** 状态锁 */
    private val stateMutex = Mutex()

    /**
     * 待回答问题封装
     */
    data class PendingQuestion(
        val question: InteractiveQuestion,
        val state: QuestionState = QuestionState.PENDING,
        val createdAt: Long = System.currentTimeMillis(),
        val answeredAt: Long? = null,
        val answer: QuestionAnswer? = null
    )

    /**
     * 问题状态
     */
    enum class QuestionState {
        /** 等待回答 */
        PENDING,
        /** 已回答 */
        ANSWERED,
        /** 已超时 */
        TIMEOUT,
        /** 已取消 */
        CANCELLED
    }

    /**
     * 问题答案
     */
    sealed class QuestionAnswer {
        data class Confirmation(val allowed: Boolean) : QuestionAnswer()
        data class SingleChoice(val selectedOption: QuestionOption) : QuestionAnswer()
        data class MultipleChoice(val selectedOptions: List<QuestionOption>) : QuestionAnswer()
        data class TextInput(val text: String) : QuestionAnswer()
        data class NumberInput(val number: Double) : QuestionAnswer()
    }

    /** 当前活跃问题ID */
    private val _activeQuestionId = MutableStateFlow<String?>(null)
    val activeQuestionId: StateFlow<String?> = _activeQuestionId.asStateFlow()

    /** 问题队列 */
    private val _questionQueue = MutableStateFlow<List<InteractiveQuestion>>(emptyList())
    val questionQueue: StateFlow<List<InteractiveQuestion>> = _questionQueue.asStateFlow()

    // ==================== 核心API ====================

    /**
     * 创建并发送问题
     *
     * @param question 问题
     * @param timeout 超时时间（毫秒）
     * @return 答案
     */
    suspend fun askQuestion(
        question: InteractiveQuestion,
        timeout: Long = question.timeout
    ): QuestionAnswer? = stateMutex.withLock {
        try {
            // 1. 添加到待回答列表
            val pendingQuestion = PendingQuestion(question)
            pendingQuestions[question.questionId] = pendingQuestion

            // 2. 更新活跃问题
            _activeQuestionId.value = question.questionId

            // 3. 更新队列显示
            updateQuestionQueue()

            // 4. 发布问题事件
            EventBus.publish(InteractiveQuestionEvent(question))

            log.info("Question created: ${question.questionId}, type=${question.questionType}")

            // 5. 等待答案（带超时）
            val answer = waitForAnswer(question.questionId, timeout)

            // 6. 更新问题状态
            val updatedPending = pendingQuestions[question.questionId]?.copy(
                state = if (answer != null) QuestionState.ANSWERED else QuestionState.TIMEOUT,
                answeredAt = System.currentTimeMillis(),
                answer = answer
            )
            if (updatedPending != null) {
                pendingQuestions[question.questionId] = updatedPending
            }

            // 7. 清理活跃问题
            _activeQuestionId.value = null
            updateQuestionQueue()

            answer
        } catch (e: CancellationException) {
            log.warn("Question cancelled: ${question.questionId}")
            val currentPending = pendingQuestions[question.questionId]
            if (currentPending != null) {
                pendingQuestions[question.questionId] = currentPending.copy(
                    state = QuestionState.CANCELLED
                )
            }
            null
        }
    }

    /**
     * 创建确认问题
     *
     * @param question 问题描述
     * @param allowDescription 允许选项描述
     * @param denyDescription 拒绝选项描述
     * @return 是否允许
     */
    suspend fun askConfirmation(
        question: String,
        allowDescription: String = "允许",
        denyDescription: String = "拒绝"
    ): Boolean {
        val q = InteractiveQuestion.confirmation(question, allowDescription, denyDescription)
        val answer = askQuestion(q)
        return (answer as? QuestionAnswer.Confirmation)?.allowed == true
    }

    /**
     * 创建单选问题
     *
     * @param question 问题描述
     * @param options 选项列表
     * @return 选中的选项
     */
    suspend fun askSingleChoice(
        question: String,
        options: List<QuestionOption>
    ): QuestionOption? {
        val q = InteractiveQuestion.singleChoice(question, options)
        val answer = askQuestion(q)
        return (answer as? QuestionAnswer.SingleChoice)?.selectedOption
    }

    /**
     * 创建文本输入问题
     *
     * @param question 问题描述
     * @param placeholder 占位符
     * @return 输入的文本
     */
    suspend fun askTextInput(
        question: String,
        placeholder: String = ""
    ): String? {
        val q = InteractiveQuestion.textInput(question)
        val answer = askQuestion(q)
        return (answer as? QuestionAnswer.TextInput)?.text
    }

    /**
     * 提交答案
     *
     * @param questionId 问题ID
     * @param answer 答案
     */
    fun submitAnswer(questionId: String, answer: QuestionAnswer) {
        scope.launch {
            stateMutex.withLock {
                val pending = pendingQuestions[questionId]
                if (pending != null && pending.state == QuestionState.PENDING) {
                    // 验证答案类型
                    val validatedAnswer = validateAnswer(pending.question, answer)
                    if (validatedAnswer != null) {
                        pendingQuestions[questionId] = pending.copy(
                            state = QuestionState.ANSWERED,
                            answeredAt = System.currentTimeMillis(),
                            answer = validatedAnswer
                        )

                        log.info("Answer submitted: questionId=$questionId")
                        EventBus.publish(QuestionAnsweredEvent(questionId, validatedAnswer))
                    } else {
                        log.warn("Invalid answer type for question: $questionId")
                    }
                }
            }
        }
    }

    /**
     * 取消问题
     *
     * @param questionId 问题ID
     */
    fun cancelQuestion(questionId: String) {
        scope.launch {
            stateMutex.withLock {
                val pending = pendingQuestions[questionId]
                if (pending != null && pending.state == QuestionState.PENDING) {
                    pendingQuestions[questionId] = pending.copy(state = QuestionState.CANCELLED)
                    _activeQuestionId.value = null
                    updateQuestionQueue()
                    log.info("Question cancelled: $questionId")
                }
            }
        }
    }

    /**
     * 获取问题状态
     *
     * @param questionId 问题ID
     * @return 问题状态
     */
    fun getQuestionState(questionId: String): QuestionState? {
        return pendingQuestions[questionId]?.state
    }

    /**
     * 获取所有待回答问题
     */
    fun getPendingQuestions(): List<PendingQuestion> {
        return pendingQuestions.values
            .filter { it.state == QuestionState.PENDING }
            .sortedBy { it.createdAt }
    }

    /**
     * 清理已完成的问题
     */
    fun cleanupCompletedQuestions() {
        scope.launch {
            stateMutex.withLock {
                val now = System.currentTimeMillis()
                val toRemove = pendingQuestions.filter { (_, pending) ->
                    pending.state != QuestionState.PENDING &&
                    (pending.answeredAt?.let { now - it > 300000 } ?: true) // 5分钟后清理
                }
                toRemove.forEach { (id, _) ->
                    pendingQuestions.remove(id)
                }
                if (toRemove.isNotEmpty()) {
                    log.info("Cleaned up ${toRemove.size} completed questions")
                }
            }
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 等待答案
     */
    private suspend fun waitForAnswer(
        questionId: String,
        timeout: Long
    ): QuestionAnswer? = kotlinx.coroutines.withTimeout(timeout * 1000) {
        var result: QuestionAnswer? = null
        while (result == null) {
            val pending = pendingQuestions[questionId]
            result = when {
                pending == null -> {
                    log.warn("Question not found: $questionId")
                    null
                }
                pending.state == QuestionState.ANSWERED -> {
                    pending.answer
                }
                pending.state == QuestionState.CANCELLED -> {
                    null
                }
                pending.state == QuestionState.TIMEOUT -> {
                    null
                }
                else -> {
                    // 继续等待
                    delay(100)
                    null
                }
            }
        }
        result
    }

    /**
     * 验证答案类型
     */
    private fun validateAnswer(
        question: InteractiveQuestion,
        answer: QuestionAnswer
    ): QuestionAnswer? {
        return when (question.questionType) {
            QuestionType.CONFIRMATION -> {
                if (answer is QuestionAnswer.Confirmation) answer else null
            }
            QuestionType.SINGLE_CHOICE -> {
                if (answer is QuestionAnswer.SingleChoice) answer else null
            }
            QuestionType.MULTIPLE_CHOICE -> {
                if (answer is QuestionAnswer.MultipleChoice) answer else null
            }
            QuestionType.TEXT_INPUT -> {
                if (answer is QuestionAnswer.TextInput) answer else null
            }
            QuestionType.NUMBER_INPUT -> {
                if (answer is QuestionAnswer.NumberInput) answer else null
            }
        }
    }

    /**
     * 更新问题队列
     */
    private fun updateQuestionQueue() {
        scope.launch {
            stateMutex.withLock {
                _questionQueue.value = pendingQuestions.values
                    .filter { it.state == QuestionState.PENDING }
                    .sortedBy { it.createdAt }
                    .map { it.question }
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        pendingQuestions.clear()
    }

    companion object {
        fun getInstance(project: Project): InteractiveRequestEngine =
            project.getService(InteractiveRequestEngine::class.java)
    }
}