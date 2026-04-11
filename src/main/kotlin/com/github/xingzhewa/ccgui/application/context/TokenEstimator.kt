package com.github.xingzhewa.ccgui.application.context

/**
 * Token 估算器
 *
 * 提供基于字符分布的动态 token 估算，支持中英文混合文本。
 *
 * 估算原理：
 * - CJK 字符（中日韩）：约 2 字符/token
 * - ASCII 字符（英文）：约 4 字符/token
 * - 混合文本：按字符分布比例加权计算
 *
 * 预留接口：后续可集成 tiktoken 等精确分词工具
 */
class TokenEstimator {

    /**
     * 估算文本的 token 数量
     *
     * @param text 待估算的文本
     * @return 估算的 token 数量，至少为 1（非空文本）
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0

        var cjkCount = 0
        var asciiCount = 0
        var otherCount = 0

        for (char in text) {
            when {
                isCjkCharacter(char) -> cjkCount++
                char.code < 128 -> asciiCount++
                else -> otherCount++
            }
        }

        // 动态比率估算
        // CJK: ~2 chars/token（汉字通常 1-2 字符构成一个语义单元）
        // ASCII: ~4 chars/token（英文单词平均约 4 字符 + 空格）
        // Other: ~3 chars/token（其他 Unicode 字符）
        val cjkTokens = cjkCount / CJK_CHAR_PER_TOKEN
        val asciiTokens = asciiCount / ASCII_CHAR_PER_TOKEN
        val otherTokens = otherCount / OTHER_CHAR_PER_TOKEN

        return (cjkTokens + asciiTokens + otherTokens).toInt().coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
    }

    /**
     * 估算文本的 token 数量（Long 版本，用于大文本）
     *
     * @param text 待估算的文本
     * @return 估算的 token 数量
     */
    fun estimateTokensLong(text: String): Long = estimateTokens(text).toLong()

    /**
     * 获取文本中 CJK 字符的比例
     *
     * @param text 文本
     * @return 0.0-1.0 的 CJK 字符比例
     */
    fun getCjkRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        val cjkCount = text.count { isCjkCharacter(it) }
        return cjkCount.toDouble() / text.length
    }

    /**
     * 获取动态字符/token 比率
     *
     * 根据文本样本中 CJK 字符比例，动态计算加权平均比率。
     * 用于与旧代码（基于固定比率）兼容。
     *
     * @param text 文本样本
     * @return 预估的字符/token 比率
     */
    fun getDynamicCharPerTokenRatio(text: String): Double {
        if (text.isEmpty()) return DEFAULT_CHAR_PER_TOKEN
        val cjkRatio = getCjkRatio(text)
        return CJK_CHAR_PER_TOKEN * cjkRatio + ASCII_CHAR_PER_TOKEN * (1.0 - cjkRatio)
    }

    /**
     * 判断字符是否为 CJK（中日韩统一表意文字）字符
     */
    private fun isCjkCharacter(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF ||    // CJK Unified Ideographs（主要汉字区）
               code in 0x3400..0x4DBF ||    // CJK Extension A
               code in 0xF900..0xFAFF ||    // CJK Compatibility Ideographs
               code in 0x3000..0x303F ||    // CJK Symbols and Punctuation
               code in 0xFF00..0xFFEF       // Halfwidth and Fullwidth Forms
    }

    companion object {
        /** 默认字符/token 比率（通用回退值） */
        const val DEFAULT_CHAR_PER_TOKEN = 3.5

        /** CJK 字符的字符/token 比率 */
        const val CJK_CHAR_PER_TOKEN = 2.0

        /** ASCII 字符的字符/token 比率 */
        const val ASCII_CHAR_PER_TOKEN = 4.0

        /** 其他 Unicode 字符的字符/token 比率 */
        const val OTHER_CHAR_PER_TOKEN = 3.0

        private val instance = TokenEstimator()

        fun getInstance(): TokenEstimator = instance
    }
}
