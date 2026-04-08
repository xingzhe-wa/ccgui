package com.github.xingzhewa.ccgui.model.interaction

import com.github.xingzhewa.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * 交互式问题
 *
 * 用于 SDK 权限请求、工具确认等需要用户交互的场景
 *
 * @param questionId 问题 ID
 * @param question 问题描述
 * @param questionType 问题类型
 * @param options 选项列表
 * @param required 是否必答
 * @param timeout 超时时间（毫秒）
 * @param metadata 额外元数据
 */
data class InteractiveQuestion(
    val questionId: String = IdGenerator.prefixedShortId("q_"),
    val question: String,
    val questionType: QuestionType,
    val options: List<QuestionOption> = emptyList(),
    val required: Boolean = true,
    val timeout: Long = 60000L,  // 默认 60 秒
    val metadata: Map<String, Any> = emptyMap()
) {

    /**
     * 是否为确认类问题
     */
    val isConfirmation: Boolean get() = questionType == QuestionType.CONFIRMATION

    /**
     * 是否为选择类问题
     */
    val isChoice: Boolean get() = questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.MULTIPLE_CHOICE

    /**
     * 是否为输入类问题
     */
    val isInput: Boolean get() = questionType == QuestionType.TEXT_INPUT || questionType == QuestionType.NUMBER_INPUT

    /**
     * 获取选项数量
     */
    val optionCount: Int get() = options.size

    companion object {
        /**
         * 创建确认问题
         */
        fun confirmation(
            question: String,
            allowDescription: String = "允许",
            denyDescription: String = "拒绝"
        ): InteractiveQuestion {
            return InteractiveQuestion(
                question = question,
                questionType = QuestionType.CONFIRMATION,
                options = listOf(
                    QuestionOption("allow", allowDescription),
                    QuestionOption("deny", denyDescription)
                )
            )
        }

        /**
         * 创建单选问题
         */
        fun singleChoice(
            question: String,
            options: List<QuestionOption>
        ): InteractiveQuestion {
            return InteractiveQuestion(
                question = question,
                questionType = QuestionType.SINGLE_CHOICE,
                options = options
            )
        }

        /**
         * 创建多选问题
         */
        fun multipleChoice(
            question: String,
            options: List<QuestionOption>
        ): InteractiveQuestion {
            return InteractiveQuestion(
                question = question,
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = options
            )
        }

        /**
         * 创建文本输入问题
         */
        fun textInput(question: String): InteractiveQuestion {
            return InteractiveQuestion(
                question = question,
                questionType = QuestionType.TEXT_INPUT
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): InteractiveQuestion? {
            return try {
                InteractiveQuestion(
                    questionId = json.get("questionId")?.asString ?: IdGenerator.prefixedShortId("q_"),
                    question = json.get("question")?.asString ?: "",
                    questionType = QuestionType.valueOf(
                        json.get("questionType")?.asString ?: "CONFIRMATION"
                    ),
                    options = json.getAsJsonArray("options")?.map {
                        val obj = it.asJsonObject
                        QuestionOption(
                            id = obj.get("id")?.asString ?: "",
                            label = obj.get("label")?.asString ?: "",
                            description = obj.get("description")?.asString
                        )
                    } ?: emptyList(),
                    required = json.get("required")?.asBoolean ?: true,
                    timeout = json.get("timeout")?.asLong ?: 60000L,
                    metadata = json.getAsJsonObject("metadata")?.let { obj ->
                        obj.entrySet().associate { entry ->
                            entry.key to entry.value.toString()
                        }
                    } ?: emptyMap()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("questionId", questionId)
            addProperty("question", question)
            addProperty("questionType", questionType.name)
            add("options", com.google.gson.JsonArray().apply {
                options.forEach { opt ->
                    add(JsonObject().apply {
                        addProperty("id", opt.id)
                        addProperty("label", opt.label)
                        opt.description?.let { addProperty("description", it) }
                    })
                }
            })
            addProperty("required", required)
            addProperty("timeout", timeout)
        }
    }
}
