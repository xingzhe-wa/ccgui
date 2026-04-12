package com.github.claudecode.ccgui.application.commit

import com.github.claudecode.ccgui.util.logger
import java.io.File
import java.util.regex.Pattern

/**
 * 差异分析器
 *
 * 分析 Git 差异，提取变更摘要、文件列表、关键变更
 */
class DiffAnalyzer {

    private val log = logger<DiffAnalyzer>()

    /**
     * 变更类型
     */
    enum class ChangeType {
        ADDED, MODIFIED, DELETED, RENAMED, COPIED
    }

    /**
     * Git 文件变更
     */
    data class GitFile(
        val path: String,
        val oldPath: String? = null,
        val changeType: ChangeType,
        val additions: Int = 0,
        val deletions: Int = 0
    )

    /**
     * 差异摘要
     */
    data class DiffSummary(
        val totalFiles: Int,
        val totalAdditions: Int,
        val totalDeletions: Int,
        val files: List<GitFile>,
        val affectedModules: List<String>,
        val keyChanges: List<String>,
        val changeTypes: Map<ChangeType, Int>
    )

    /**
     * 分析差异
     */
    fun analyzeDiff(diff: String): DiffSummary {
        val files = mutableListOf<GitFile>()
        var totalAdditions = 0
        var totalDeletions = 0
        val changeTypes = mutableMapOf<ChangeType, Int>()

        val lines = diff.lines()
        var currentFile: String? = null
        var currentChangeType: ChangeType = ChangeType.MODIFIED
        var currentAdditions = 0
        var currentDeletions = 0

        for (line in lines) {
            when {
                // 新文件 diff --git a/file b/file
                line.startsWith("diff --git a/") -> {
                    // 保存上一个文件
                    currentFile?.let { path ->
                        files.add(GitFile(
                            path = path,
                            changeType = currentChangeType,
                            additions = currentAdditions,
                            deletions = currentDeletions
                        ))
                        totalAdditions += currentAdditions
                        totalDeletions += currentDeletions
                        changeTypes[currentChangeType] = (changeTypes[currentChangeType] ?: 0) + 1
                    }

                    // 解析新文件
                    val filePath = line.substringAfter("diff --git a/").substringBefore(" b/")
                    currentFile = filePath
                    currentChangeType = ChangeType.MODIFIED
                    currentAdditions = 0
                    currentDeletions = 0

                }
                // 文件重命名
                line.startsWith("rename from ") -> {
                    currentChangeType = ChangeType.RENAMED
                }
                // 新文件
                line.startsWith("new file mode") -> {
                    currentChangeType = ChangeType.ADDED
                }
                // 删除文件
                line.startsWith("deleted file mode") -> {
                    currentChangeType = ChangeType.DELETED
                }
                // 复制文件
                line.startsWith("copy from ") -> {
                    currentChangeType = ChangeType.COPIED
                }
                // 统计行数变化
                line.startsWith("+") && !line.startsWith("+++") -> {
                    currentAdditions++
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    currentDeletions++
                }
            }
        }

        // 保存最后一个文件
        currentFile?.let { path ->
            files.add(GitFile(
                path = path,
                changeType = currentChangeType,
                additions = currentAdditions,
                deletions = currentDeletions
            ))
            totalAdditions += currentAdditions
            totalDeletions += currentDeletions
            changeTypes[currentChangeType] = (changeTypes[currentChangeType] ?: 0) + 1
        }

        return DiffSummary(
            totalFiles = files.size,
            totalAdditions = totalAdditions,
            totalDeletions = totalDeletions,
            files = files,
            affectedModules = extractAffectedModules(files),
            keyChanges = extractKeyChanges(diff, files),
            changeTypes = changeTypes
        )
    }

    /**
     * 提取受影响的模块
     */
    private fun extractAffectedModules(files: List<GitFile>): List<String> {
        val modules = mutableSetOf<String>()

        for (file in files) {
            val path = file.path

            // 根据路径推断模块
            when {
                // 前端模块
                path.contains("/src/main/webapp/") || path.contains("/webapp/src/") -> {
                    modules.add("webapp")
                }
                // 后端模块
                path.contains("/src/main/java/") || path.contains("/src/") -> {
                    modules.add("backend")
                    // 提取包名作为子模块
                    val packageMatch = Regex("/src/main/java/(.+?)/").find(path)
                    packageMatch?.groupValues?.get(1)?.let { pkg ->
                        if (pkg.isNotEmpty()) modules.add(pkg.substringBefore("."))
                    }
                }
                // 配置模块
                path.contains("/src/main/resources/") || path.contains("/resources/") -> {
                    modules.add("config")
                }
                // 测试模块
                path.contains("/src/test/") || path.contains("/test/") -> {
                    modules.add("test")
                }
                // 文档
                path.contains("/docs/") || path.endsWith(".md") -> {
                    modules.add("docs")
                }
                // 构建文件
                path.contains("pom.xml") || path.contains("build.gradle") -> {
                    modules.add("build")
                }
            }
        }

        return modules.toList()
    }

    /**
     * 提取关键变更
     */
    private fun extractKeyChanges(diff: String, files: List<GitFile>): List<String> {
        val keyChanges = mutableListOf<String>()

        // 根据变更类型分类
        val addedFiles = files.filter { it.changeType == ChangeType.ADDED }
        val deletedFiles = files.filter { it.changeType == ChangeType.DELETED }
        val modifiedFiles = files.filter { it.changeType == ChangeType.MODIFIED }
        val renamedFiles = files.filter { it.changeType == ChangeType.RENAMED }

        if (addedFiles.isNotEmpty()) {
            keyChanges.add("新增 ${addedFiles.size} 个文件")
            addedFiles.take(3).forEach { file ->
                keyChanges.add("+ ${file.path}")
            }
            if (addedFiles.size > 3) {
                keyChanges.add("  ... 还有 ${addedFiles.size - 3} 个文件")
            }
        }

        if (deletedFiles.isNotEmpty()) {
            keyChanges.add("删除 ${deletedFiles.size} 个文件")
            deletedFiles.take(3).forEach { file ->
                keyChanges.add("- ${file.path}")
            }
            if (deletedFiles.size > 3) {
                keyChanges.add("  ... 还有 ${deletedFiles.size - 3} 个文件")
            }
        }

        if (modifiedFiles.isNotEmpty()) {
            // 统计修改行数最多的文件
            val mostModified = modifiedFiles.sortedByDescending { it.additions + it.deletions }.take(3)
            keyChanges.add("修改 ${modifiedFiles.size} 个文件")
            mostModified.forEach { file ->
                keyChanges.add("~ ${file.path} (+${file.additions} -${file.deletions})")
            }
        }

        if (renamedFiles.isNotEmpty()) {
            keyChanges.add("重命名 ${renamedFiles.size} 个文件")
        }

        return keyChanges
    }

    /**
     * 生成简短的变更描述
     */
    fun generateShortDescription(summary: DiffSummary): String {
        val parts = mutableListOf<String>()

        summary.changeTypes[ChangeType.ADDED]?.let { count ->
            if (count > 0) parts.add("$count 新增")
        }
        summary.changeTypes[ChangeType.MODIFIED]?.let { count ->
            if (count > 0) parts.add("$count 修改")
        }
        summary.changeTypes[ChangeType.DELETED]?.let { count ->
            if (count > 0) parts.add("$count 删除")
        }
        summary.changeTypes[ChangeType.RENAMED]?.let { count ->
            if (count > 0) parts.add("$count 重命名")
        }

        return parts.joinToString(", ")
    }

    /**
     * 检测变更类型
     */
    fun detectIntent(diff: String): List<String> {
        val intents = mutableListOf<String>()
        val lines = diff.lines()

        // 模式匹配常见的变更意图
        val patterns = listOf(
            Pattern.compile("feat.*?:") to "新功能",
            Pattern.compile("fix.*?:") to "bug修复",
            Pattern.compile("docs?.*?:") to "文档更新",
            Pattern.compile("style.*?:") to "代码格式调整",
            Pattern.compile("refactor.*?:") to "代码重构",
            Pattern.compile("perf.*?:") to "性能优化",
            Pattern.compile("test.*?:") to "测试相关",
            Pattern.compile("chore.*?:") to "构建/工具变更",
            Pattern.compile("breaking") to "破坏性变更"
        )

        for (line in lines) {
            for ((pattern, intent) in patterns) {
                if (pattern.matcher(line).find()) {
                    if (!intents.contains(intent)) {
                        intents.add(intent)
                    }
                }
            }
        }

        // 基于文件路径推断
        if (intents.isEmpty()) {
            val hasNewFiles = lines.any { it.startsWith("new file mode") }
            val hasDeletedFiles = lines.any { it.startsWith("deleted file mode") }

            when {
                hasNewFiles && hasDeletedFiles -> intents.add("代码重构")
                hasNewFiles -> intents.add("功能扩展")
                hasDeletedFiles -> intents.add("功能移除")
                else -> intents.add("代码修改")
            }
        }

        return intents
    }
}
