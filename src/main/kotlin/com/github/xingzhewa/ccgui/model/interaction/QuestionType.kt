package com.github.xingzhewa.ccgui.model.interaction

/**
 * 问题类型枚举
 */
enum class QuestionType {
    /** 确认类问题 - 是/否 */
    CONFIRMATION,

    /** 单选问题 */
    SINGLE_CHOICE,

    /** 多选问题 */
    MULTIPLE_CHOICE,

    /** 文本输入问题 */
    TEXT_INPUT,

    /** 数字输入问题 */
    NUMBER_INPUT
}

/**
 * 问题选项
 */
data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null
)
