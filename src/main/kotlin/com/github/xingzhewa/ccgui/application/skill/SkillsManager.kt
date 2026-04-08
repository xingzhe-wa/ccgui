package com.github.xingzhewa.ccgui.application.skill

import com.github.xingzhewa.ccgui.infrastructure.eventbus.EventBus
import com.github.xingzhewa.ccgui.infrastructure.eventbus.SkillExecutedEvent
import com.github.xingzhewa.ccgui.model.skill.ExecutionContext
import com.github.xingzhewa.ccgui.model.skill.Skill
import com.github.xingzhewa.ccgui.model.skill.SkillCategory
import com.github.xingzhewa.ccgui.model.skill.SkillResult
import com.github.xingzhewa.ccgui.model.skill.SkillScope
import com.github.xingzhewa.ccgui.util.JsonUtils
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Skills 管理器
 *
 * 负责:
 * - Skill 的增删改查
 * - Skill 分类管理
 * - Skill 作用域管理（全局/项目）
 * - Skill 启用/禁用
 * - 内置 Skill 管理
 *
 * @param project IntelliJ项目实例
 */
@Service(Service.Level.PROJECT)
class SkillsManager(private val project: Project) : Disposable {

    private val log = logger<SkillsManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 所有 Skills (key: skillId) */
    private val skills = ConcurrentHashMap<String, Skill>()

    /** 按分类索引的 Skills */
    private val skillsByCategory = ConcurrentHashMap<SkillCategory, MutableSet<String>>()

    /** 按作用域索引的 Skills */
    private val skillsByScope = ConcurrentHashMap<SkillScope, MutableSet<String>>()

    /** 当前活跃的 Skill 扽数 */
    private val _activeExecutions = MutableStateFlow<Set<String>>(emptySet())
    val activeExecutions: StateFlow<Set<String>> = _activeExecutions.asStateFlow()

    /** 所有 Skills 列表 */
    private val _allSkills = MutableStateFlow<List<Skill>>(emptyList())
    val allSkills: StateFlow<List<Skill>> = _allSkills.asStateFlow()

    // ==================== 初始化 ====================

    init {
        loadBuiltinSkills()
        loadSkillsFromStorage()
        log.info("SkillsManager initialized for project: ${project.name}")
    }

    /**
     * 加载内置 Skills
     */
    private fun loadBuiltinSkills() {
        val builtinSkills = listOf(
            Skill.codeGeneration(
                name = "代码生成器",
                description = "根据描述生成代码",
                prompt = "请根据以下描述生成代码：\n\n{description}\n\n要求：\n1. 代码风格一致\n2. 包含必要注释\n3. 考虑边界情况"
            ),
            Skill.codeReview(
                name = "代码审查员",
                description = "审查代码并提供改进建议",
                prompt = "请审查以下代码，并提供改进建议：\n\n{code}\n\n重点关注：\n1. 代码质量\n2. 潜在bug\n3. 性能优化\n4. 安全问题"
            ),
            Skill(
                name = "单元测试生成",
                description = "为代码生成单元测试",
                icon = "🧪",
                category = SkillCategory.TESTING,
                prompt = "请为以下代码生成完整的单元测试：\n\n{code}\n\n测试框架：{framework}\n\n要求覆盖主要场景。"
            ),
            Skill(
                name = "代码重构",
                description = "重构代码以提升质量",
                icon = "🔧",
                category = SkillCategory.REFACTORING,
                prompt = "请重构以下代码以提升质量和可读性：\n\n{code}\n\n保持原有功能不变。"
            ),
            Skill(
                name = "文档生成",
                description = "为代码生成文档",
                icon = "📚",
                category = SkillCategory.DOCUMENTATION,
                prompt = "请为以下代码生成清晰的文档：\n\n{code}\n\n包括：\n1. 功能说明\n2. 参数说明\n3. 返回值说明\n4. 使用示例"
            ),
            Skill(
                name = "Bug 诊断",
                description = "分析代码中的问题",
                icon = "🐛",
                category = SkillCategory.DEBUGGING,
                prompt = "请分析以下代码中的问题：\n\n{code}\n\n问题描述：{problem}\n\n提供诊断和修复建议。"
            )
        )

        builtinSkills.forEach { skill ->
            addSkill(skill)
        }

        log.info("Loaded ${builtinSkills.size} builtin skills")
    }

    /**
     * 从存储加载 Skills
     */
    private fun loadSkillsFromStorage() {
        // TODO: 从持久化存储加载
        log.debug("Loading skills from storage...")
    }

    // ==================== 核心 API ====================

    /**
     * 添加 Skill
     *
     * @param skill Skill
     * @return 是否成功
     */
    fun addSkill(skill: Skill): Boolean {
        if (skills.containsKey(skill.id)) {
            log.warn("Skill already exists: ${skill.id}")
            return false
        }

        skills[skill.id] = skill

        // 更新索引
        skillsByCategory.getOrPut(skill.category) { mutableSetOf() }.add(skill.id)
        skillsByScope.getOrPut(skill.scope) { mutableSetOf() }.add(skill.id)

        updateAllSkills()

        log.info("Skill added: ${skill.id} - ${skill.name}")

        return true
    }

    /**
     * 更新 Skill
     *
     * @param skill 更新后的 Skill
     * @return 是否成功
     */
    fun updateSkill(skill: Skill): Boolean {
        if (!skills.containsKey(skill.id)) {
            log.warn("Skill not found: ${skill.id}")
            return false
        }

        val oldSkill = skills[skill.id]!!

        // 更新索引
        if (oldSkill.category != skill.category) {
            skillsByCategory[oldSkill.category]?.remove(skill.id)
            skillsByCategory.getOrPut(skill.category) { mutableSetOf() }.add(skill.id)
        }

        if (oldSkill.scope != skill.scope) {
            skillsByScope[oldSkill.scope]?.remove(skill.id)
            skillsByScope.getOrPut(skill.scope) { mutableSetOf() }.add(skill.id)
        }

        skills[skill.id] = skill.copy(updatedAt = System.currentTimeMillis())
        updateAllSkills()

        log.info("Skill updated: ${skill.id}")

        return true
    }

    /**
     * 删除 Skill
     *
     * @param skillId Skill ID
     * @return 是否成功
     */
    fun deleteSkill(skillId: String): Boolean {
        val skill = skills.remove(skillId) ?: return false

        // 更新索引
        skillsByCategory[skill.category]?.remove(skillId)
        skillsByScope[skill.scope]?.remove(skillId)

        updateAllSkills()

        log.info("Skill deleted: $skillId")

        return true
    }

    /**
     * 获取 Skill
     *
     * @param skillId Skill ID
     * @return Skill
     */
    fun getSkill(skillId: String): Skill? {
        return skills[skillId]
    }

    /**
     * 按 ID 列表获取 Skills
     *
     * @param skillIds Skill ID 列表
     * @return Skill 列表
     */
    fun getSkills(skillIds: List<String>): List<Skill> {
        return skillIds.mapNotNull { skills[it] }
    }

    /**
     * 获取所有 Skills
     *
     * @return 所有 Skills
     */
    fun getAllSkills(): List<Skill> {
        return skills.values.toList()
    }

    /**
     * 按分类获取 Skills
     *
     * @param category 分类
     * @return Skills
     */
    fun getSkillsByCategory(category: SkillCategory): List<Skill> {
        val skillIds = skillsByCategory[category] ?: return emptyList()
        return skillIds.mapNotNull { skills[it] }
    }

    /**
     * 按作用域获取 Skills
     *
     * @param scope 作用域
     * @return Skills
     */
    fun getSkillsByScope(scope: SkillScope): List<Skill> {
        val skillIds = skillsByScope[scope] ?: return emptyList()
        return skillIds.mapNotNull { skills[it] }
    }

    /**
     * 获取启用的 Skills
     *
     * @return 启用的 Skills
     */
    fun getEnabledSkills(): List<Skill> {
        return skills.values.filter { it.enabled }
    }

    /**
     * 启用/禁用 Skill
     *
     * @param skillId Skill ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    fun setSkillEnabled(skillId: String, enabled: Boolean): Boolean {
        val skill = skills[skillId] ?: return false
        return updateSkill(skill.copy(enabled = enabled))
    }

    /**
     * 搜索 Skills
     *
     * @param query 搜索关键词
     * @return 匹配的 Skills
     */
    fun searchSkills(query: String): List<Skill> {
        val lowerQuery = query.lowercase()
        return skills.values.filter { skill ->
            skill.name.lowercase().contains(lowerQuery) ||
            skill.description.lowercase().contains(lowerQuery) ||
            skill.category.name.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 导出 Skill
     *
     * @param skillId Skill ID
     * @return JSON 字符串
     */
    fun exportSkill(skillId: String): String? {
        val skill = skills[skillId] ?: return null
        return JsonUtils.gson.toJson(skill.toJson())
    }

    /**
     * 导入 Skill
     *
     * @param json JSON 字符串
     * @return 是否成功
     */
    fun importSkill(json: String): Boolean {
        return try {
            val jsonObject = JsonUtils.gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val skill = Skill.fromJson(jsonObject) ?: return false
            addSkill(skill)
        } catch (e: Exception) {
            log.error("Failed to import skill", e)
            false
        }
    }

    /**
     * 批量导入 Skills
     *
     * @param jsons JSON 字符串列表
     * @return 成功导入数量
     */
    fun importSkills(jsons: List<String>): Int {
        var count = 0
        jsons.forEach { json ->
            if (importSkill(json)) count++
        }
        return count
    }

    // ==================== 内部方法 ====================

    /**
     * 更新所有 Skills 列表
     */
    private fun updateAllSkills() {
        _allSkills.value = skills.values
            .sortedByDescending { it.updatedAt }
    }

    override fun dispose() {
        scope.cancel()
        skills.clear()
        skillsByCategory.clear()
        skillsByScope.clear()
    }

    companion object {
        fun getInstance(project: Project): SkillsManager =
            project.getService(SkillsManager::class.java)
    }
}

/**
 * Skill 事件
 */
sealed class SkillEvent {
    data class Added(val skill: Skill) : SkillEvent()
    data class Updated(val skill: Skill) : SkillEvent()
    data class Deleted(val skillId: String) : SkillEvent()
    data class Executed(val skillId: String, val result: SkillResult) : SkillEvent()
}
