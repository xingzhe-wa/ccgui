package com.github.xingzhewa.ccgui.application.commit

import com.github.xingzhewa.ccgui.adaptation.sdk.ClaudeCodeClient
import com.github.xingzhewa.ccgui.adaptation.sdk.SdkOptions
import com.github.xingzhewa.ccgui.infrastructure.git.GitClient
import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Commit 生成服务
 *
 * 通过分析 Git 差异自动生成符合规范的 Commit Message
 */
class CommitService(private val project: Project) {

    private val log = logger<CommitService>()
    private val diffAnalyzer = DiffAnalyzer()
    private val gitClient by lazy { GitClient.getInstance(project) }
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 状态 */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 生成选项 */
    data class GenerateOptions(
        val templateName: String = "conventional",
        val model: String = "claude-sonnet-4-20250514",
        val maxLength: Int = 72,
        val includeScope: Boolean = true,
        val includeBody: Boolean = true,
        val includeFooter: Boolean = false
    )

    /** 生成结果 */
    sealed class GenerateResult {
        data class Success(val message: String, val summary: DiffAnalyzer.DiffSummary) : GenerateResult()
        data class Error(val message: String) : GenerateResult()
    }

    /**
     * 生成 Commit Message
     */
    suspend fun generate(options: GenerateOptions = GenerateOptions()): GenerateResult {
        if (_isGenerating.value) {
            return GenerateResult.Error("Already generating")
        }

        _isGenerating.value = true

        return try {
            withContext(Dispatchers.IO) {
                // 1. 检查 Git 状态
                if (!gitClient.isGitRepository()) {
                    return@withContext GenerateResult.Error("Not a Git repository")
                }

                // 2. 获取暂存的变更
                val stagedFiles = gitClient.getStagedFiles()
                if (stagedFiles.isEmpty()) {
                    return@withContext GenerateResult.Error("No staged files")
                }

                // 3. 获取差异
                val diff = gitClient.getStagedDiff()
                if (diff.isBlank()) {
                    return@withContext GenerateResult.Error("No changes to commit")
                }

                // 4. 分析差异
                val summary = diffAnalyzer.analyzeDiff(diff)

                // 5. 获取上下文
                val branchName = gitClient.getCurrentBranch()
                val recentCommits = gitClient.getRecentCommits(5)
                val projectContext = project.name

                // 6. 选择模板
                val template = getTemplate(options.templateName)

                // 7. 填充模板生成提示词
                val prompt = buildPrompt(template, summary, branchName, recentCommits, projectContext)

                // 8. 调用 Claude API 生成
                val generatedMessage = callClaudeApi(prompt, options.model)

                // 9. 后处理（截断、格式化）
                val formattedMessage = postProcess(generatedMessage, options)

                _isGenerating.value = false
                GenerateResult.Success(formattedMessage, summary)
            }
        } catch (e: Exception) {
            log.error("Failed to generate commit message", e)
            _isGenerating.value = false
            GenerateResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 获取模板
     */
    private fun getTemplate(templateName: String): CommitTemplate {
        return when (templateName.lowercase()) {
            "conventional" -> ConventionalTemplate
            "gitmoji" -> GitmojiTemplate
            "angular" -> AngularTemplate
            "emoji" -> EmojiTemplate
            else -> ConventionalTemplate
        }
    }

    /**
     * 填充提示词
     */
    private fun buildPrompt(
        template: CommitTemplate,
        summary: DiffAnalyzer.DiffSummary,
        branchName: String,
        recentCommits: List<String>,
        projectContext: String
    ): String {
        val intent = diffAnalyzer.detectIntent(summary.toString())
        val shortDesc = diffAnalyzer.generateShortDescription(summary)

        return """
            |You are a commit message generator. Analyze the following git diff and generate a commit message.
            |
            |## Format
            |${template.formatDescription}
            |
            |## Context
            |Branch: $branchName
            |Project: $projectContext
            |Detected Intent: ${intent.joinToString(", ")}
            |
            |## Recent Commits (for style reference)
            |${recentCommits.take(3).joinToString("\n") { "- $it" }}
            |
            |## Change Summary
            |- Files changed: ${summary.totalFiles}
            |- Additions: +${summary.totalAdditions}
            |- Deletions: -${summary.totalDeletions}
            |- Affected modules: ${summary.affectedModules.joinToString(", ")}
            |
            |## Key Changes
            |${summary.keyChanges.joinToString("\n")}
            |
            |## Diff
            |```diff
            |${summary.toString().take(2000)}
            |```
            |
            |Generate a concise, descriptive commit message following the ${template.name} format:
        """.trimMargin()
    }

    /**
     * 调用 Claude API
     */
    private suspend fun callClaudeApi(prompt: String, model: String): String {
        return try {
            val client = ClaudeCodeClient.getInstance(project)
            val sdkOptions = SdkOptions(model = model)
            val sdkResult = client.sendMessage(prompt, sdkOptions)
            val resultText = sdkResult.getOrNull()?.result?.trim() ?: ""
            resultText.ifEmpty { "chore: update code" }
        } catch (e: Exception) {
            log.warn("Claude API call failed, using fallback: ${e.message}")
            "chore: update code"
        }
    }

    /**
     * 后处理
     */
    private fun postProcess(message: String, options: GenerateOptions): String {
        var result = message.trim()

        // 移除可能的引号
        result = result.removeSurrounding("\"")

        // 截断到最大长度（按单词截断）
        if (result.length > options.maxLength) {
            val truncated = result.take(options.maxLength)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > options.maxLength * 0.8) {
                result = truncated.substring(0, lastSpace)
            }
        }

        // 确保以句号结尾
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result += "."
        }

        return result
    }

    /**
     * 获取变更统计
     */
    fun getChangeStats(): Map<String, Any> {
        return try {
            val diff = gitClient.getStagedDiff()
            if (diff.isBlank()) {
                return emptyMap()
            }
            val summary = diffAnalyzer.analyzeDiff(diff)
            mapOf(
                "files" to summary.totalFiles,
                "additions" to summary.totalAdditions,
                "deletions" to summary.totalDeletions,
                "modules" to summary.affectedModules,
                "types" to summary.changeTypes.mapKeys { it.key.name }
            )
        } catch (e: Exception) {
            log.warn("Failed to get change stats: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Commit 模板接口
     */
    interface CommitTemplate {
        val name: String
        val formatDescription: String
    }

    /**
     * Conventional Commits 模板
     */
    object ConventionalTemplate : CommitTemplate {
        override val name = "Conventional Commits"
        override val formatDescription = """
            |<type>(<scope>): <description>
            |
            |<type>: feat, fix, docs, style, refactor, perf, test, chore
            |<scope>: (optional) affected module
            |<description>: short summary in imperative mood
        """.trimMargin()
    }

    /**
     * Gitmoji 模板
     */
    object GitmojiTemplate : CommitTemplate {
        override val name = "Gitmoji"
        override val formatDescription = """
            |<gitmoji> <type>(<scope>): <description>
            |
            |✨ :sparkles: - Introducing new features
            |🐛 :bug: - Fixing a bug
            |📝 :memo: - Writing docs
            |🎨 :art: - Improving structure/format
            |⚡ :zap: - Improving performance
            |🔥 :fire: - Removing code/files
        """.trimMargin()
    }

    /**
     * Angular 模板
     */
    object AngularTemplate : CommitTemplate {
        override val name = "Angular"
        override val formatDescription = """
            |<type>(<scope>): <subject>
            |
            |<body>
            |
            |<footer>
            |
            |Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
        """.trimMargin()
    }

    /**
     * Emoji 模板
     */
    object EmojiTemplate : CommitTemplate {
        override val name = "Emoji"
        override val formatDescription = """
            |:<emoji>: <type>(<scope>): <description>
            |
            |Types: 🎨:art, 🐛:bug, ✨:sparkles, 📝:memo, ⚡:zap, 🔥:fire, 🚀:rocket, 🛠️:tool
        """.trimMargin()
    }

    companion object {
        fun getInstance(project: Project): CommitService =
            project.getService(CommitService::class.java)
    }
}
