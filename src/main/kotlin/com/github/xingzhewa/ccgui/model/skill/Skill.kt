package com.github.xingzhewa.ccgui.model.skill

import com.github.xingzhewa.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * Skill 分类枚举
 */
enum class SkillCategory {
    CODE_GENERATION,
    CODE_REVIEW,
    REFACTORING,
    TESTING,
    DOCUMENTATION,
    DEBUGGING,
    PERFORMANCE
}

/**
 * 变量类型枚举
 */
enum class VariableType {
    TEXT,
    NUMBER,
    ENUM,
    BOOLEAN,
    CODE
}

/**
 * Skill 作用域枚举
 */
enum class SkillScope {
    GLOBAL,
    PROJECT
}

/**
 * Skill 变量定义
 */
data class SkillVariable(
    val name: String,
    val type: VariableType,
    val defaultValue: Any? = null,
    val required: Boolean = false,
    val options: List<Any> = emptyList(),
    val placeholder: String? = null,
    val description: String? = null
) {
    companion object {
        fun fromJson(json: JsonObject): SkillVariable {
            return SkillVariable(
                name = json.get("name")?.asString ?: "",
                type = VariableType.valueOf(json.get("type")?.asString ?: "TEXT"),
                defaultValue = json.get("defaultValue")?.let {
                    if (it.isJsonPrimitive) {
                        it.asString
                    } else null
                },
                required = json.get("required")?.asBoolean ?: false,
                options = json.getAsJsonArray("options")?.map { it.asString } ?: emptyList(),
                placeholder = json.get("placeholder")?.asString,
                description = json.get("description")?.asString
            )
        }
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("type", type.name)
            defaultValue?.let { addProperty("defaultValue", it.toString()) }
            addProperty("required", required)
            if (options.isNotEmpty()) {
                add("options", com.google.gson.JsonArray().apply {
                    options.forEach { add(it.toString()) }
                })
            }
            placeholder?.let { addProperty("placeholder", it) }
            description?.let { addProperty("description", it) }
        }
    }
}

/**
 * Skill 定义
 *
 * @param id Skill ID
 * @param name Skill 名称
 * @param description Skill 描述
 * @param icon 图标标识
 * @param category 分类
 * @param prompt 提示词模板
 * @param variables 变量列表
 * @param shortcut 快捷键
 * @param enabled 是否启用
 * @param scope 作用域
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
data class Skill(
    val id: String = IdGenerator.skillId(),
    val name: String,
    val description: String,
    val icon: String = "📝",
    val category: SkillCategory = SkillCategory.CODE_GENERATION,
    val prompt: String,
    val variables: List<SkillVariable> = emptyList(),
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val scope: SkillScope = SkillScope.GLOBAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    companion object {
        /**
         * 创建代码生成 Skill
         */
        fun codeGeneration(name: String, description: String, prompt: String): Skill {
            return Skill(
                name = name,
                description = description,
                icon = "⚡",
                category = SkillCategory.CODE_GENERATION,
                prompt = prompt
            )
        }

        /**
         * 创建代码审查 Skill
         */
        fun codeReview(name: String, description: String, prompt: String): Skill {
            return Skill(
                name = name,
                description = description,
                icon = "🔍",
                category = SkillCategory.CODE_REVIEW,
                prompt = prompt
            )
        }

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): Skill? {
            return try {
                Skill(
                    id = json.get("id")?.asString ?: IdGenerator.skillId(),
                    name = json.get("name")?.asString ?: "",
                    description = json.get("description")?.asString ?: "",
                    icon = json.get("icon")?.asString ?: "📝",
                    category = SkillCategory.valueOf(
                        json.get("category")?.asString ?: "CODE_GENERATION"
                    ),
                    prompt = json.get("prompt")?.asString ?: "",
                    variables = json.getAsJsonArray("variables")?.map {
                        SkillVariable.fromJson(it.asJsonObject)
                    } ?: emptyList(),
                    shortcut = json.get("shortcut")?.asString,
                    enabled = json.get("enabled")?.asBoolean ?: true,
                    scope = SkillScope.valueOf(
                        json.get("scope")?.asString ?: "GLOBAL"
                    ),
                    createdAt = json.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                    updatedAt = json.get("updatedAt")?.asLong ?: System.currentTimeMillis()
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
            addProperty("id", id)
            addProperty("name", name)
            addProperty("description", description)
            addProperty("icon", icon)
            addProperty("category", category.name)
            addProperty("prompt", prompt)
            add("variables", com.google.gson.JsonArray().apply {
                variables.forEach { add(it.toJson()) }
            })
            shortcut?.let { addProperty("shortcut", it) }
            addProperty("enabled", enabled)
            addProperty("scope", scope.name)
            addProperty("createdAt", createdAt)
            addProperty("updatedAt", updatedAt)
        }
    }
}

/**
 * Skill 执行上下文
 */
data class ExecutionContext(
    val variables: Map<String, Any> = emptyMap(),
    val attachments: List<Any> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Skill 执行结果
 */
data class SkillResult(
    val skillId: String,
    val response: Any? = null,
    val executionTime: Long = 0,
    val tokensUsed: Int = 0,
    val success: Boolean = true,
    val error: String? = null
)
