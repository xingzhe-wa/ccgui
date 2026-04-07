package com.github.xingzhewa.ccgui.model.config

import com.google.gson.JsonObject

/**
 * 应用总配置
 */
data class AppConfig(
    val currentThemeId: String = "jetbrains-dark",
    val conversationMode: ConversationMode = ConversationMode.AUTO,
    val modelConfig: ModelConfig = ModelConfig(),
    val autoConnect: Boolean = true,
    val streamOutput: Boolean = true,
    val showLineNumbers: Boolean = true,
    val enableSpellCheck: Boolean = false,
    val maxSessionHistory: Int = 100,
    val autoSaveInterval: Long = 30000L,  // 30 秒
    val toolWindowAnchor: String = "right"  // left / right / bottom
) {

    companion object {
        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): AppConfig {
            return AppConfig(
                currentThemeId = json.get("currentThemeId")?.asString ?: "jetbrains-dark",
                conversationMode = json.get("conversationMode")?.asString?.let {
                    ConversationMode.valueOf(it)
                } ?: ConversationMode.AUTO,
                modelConfig = json.getAsJsonObject("modelConfig")?.let {
                    ModelConfig.fromJson(it)
                } ?: ModelConfig(),
                autoConnect = json.get("autoConnect")?.asBoolean ?: true,
                streamOutput = json.get("streamOutput")?.asBoolean ?: true,
                showLineNumbers = json.get("showLineNumbers")?.asBoolean ?: true,
                enableSpellCheck = json.get("enableSpellCheck")?.asBoolean ?: false,
                maxSessionHistory = json.get("maxSessionHistory")?.asInt ?: 100,
                autoSaveInterval = json.get("autoSaveInterval")?.asLong ?: 30000L,
                toolWindowAnchor = json.get("toolWindowAnchor")?.asString ?: "right"
            )
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("currentThemeId", currentThemeId)
            addProperty("conversationMode", conversationMode.name)
            add("modelConfig", modelConfig.toJson())
            addProperty("autoConnect", autoConnect)
            addProperty("streamOutput", streamOutput)
            addProperty("showLineNumbers", showLineNumbers)
            addProperty("enableSpellCheck", enableSpellCheck)
            addProperty("maxSessionHistory", maxSessionHistory)
            addProperty("autoSaveInterval", autoSaveInterval)
            addProperty("toolWindowAnchor", toolWindowAnchor)
        }
    }
}
