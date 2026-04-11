package com.github.xingzhewa.ccgui.infrastructure.git

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Git 客户端
 *
 * 封装 Git 操作，提供统一的 Git 操作接口
 */
class GitClient(private val project: Project) {

    private val log = logger<GitClient>()

    /** Git 工作目录 */
    private val workingDirectory: File?
        get() = project.basePath?.let { File(it) }

    /**
     * 检查是否为 Git 仓库
     */
    fun isGitRepository(): Boolean {
        val dir = workingDirectory ?: return false
        return File(dir, ".git").exists()
    }

    /**
     * 获取当前分支名
     */
    fun getCurrentBranch(): String {
        return runGitCommand("rev-parse", "--abbrev-ref", "HEAD")?.trim() ?: "unknown"
    }

    /**
     * 获取暂存的文件列表
     */
    fun getStagedFiles(): List<GitFileInfo> {
        val output = runGitCommand("diff", "--cached", "--name-status") ?: return emptyList()
        return parseNameStatus(output)
    }

    /**
     * 获取未暂存的文件列表
     */
    fun getUnstagedFiles(): List<GitFileInfo> {
        val output = runGitCommand("diff", "--name-status") ?: return emptyList()
        return parseNameStatus(output)
    }

    /**
     * 获取未跟踪的文件列表
     */
    fun getUntrackedFiles(): List<String> {
        val output = runGitCommand("ls-files", "--others", "--exclude-standard") ?: return emptyList()
        return output.lines().filter { it.isNotBlank() }
    }

    /**
     * 获取暂存的差异
     */
    fun getStagedDiff(): String {
        return runGitCommand("diff", "--cached") ?: ""
    }

    /**
     * 获取未暂存的差异
     */
    fun getUnstagedDiff(): String {
        return runGitCommand("diff") ?: ""
    }

    /**
     * 获取完整的差异（包括未跟踪文件）
     */
    fun getFullDiff(): String {
        val staged = getStagedDiff()
        val unstaged = getUnstagedDiff()
        return buildString {
            if (staged.isNotBlank()) {
                appendLine("=== Staged Changes ===")
                append(staged)
                appendLine()
            }
            if (unstaged.isNotBlank()) {
                appendLine("=== Unstaged Changes ===")
                append(unstaged)
            }
        }
    }

    /**
     * 获取最近 N 条提交记录
     */
    fun getRecentCommits(count: Int = 10): List<String> {
        val output = runGitCommand(
            "log",
            "--oneline",
            "-$count",
            "--pretty=format:%s"
        ) ?: return emptyList()
        return output.lines().filter { it.isNotBlank() }
    }

    /**
     * 获取最近 N 条提交的详细信息
     */
    fun getRecentCommitDetails(count: Int = 10): List<CommitInfo> {
        val output = runGitCommand(
            "log",
            "-$count",
            "--pretty=format:%H|%s|%an|%ae|%ai"
        ) ?: return emptyList()

        return output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 5) {
                CommitInfo(
                    hash = parts[0],
                    message = parts[1],
                    authorName = parts[2],
                    authorEmail = parts[3],
                    date = parts[4]
                )
            } else null
        }
    }

    /**
     * 获取文件在特定提交中的内容
     */
    fun getFileAtCommit(filePath: String, commitHash: String = "HEAD"): String? {
        return runGitCommand("show", "$commitHash:$filePath")
    }

    /**
     * 获取文件的变更统计
     */
    fun getFileStats(filePath: String): FileStats? {
        val output = runGitCommand("diff", "--numstat", filePath) ?: return null
        val parts = output.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            return FileStats(
                additions = parts[0].toIntOrNull() ?: 0,
                deletions = parts[1].toIntOrNull() ?: 0
            )
        }
        return null
    }

    /**
     * 暂存文件
     */
    fun stageFile(filePath: String): Boolean {
        val result = runGitCommand("add", filePath)
        return result != null
    }

    /**
     * 取消暂存文件
     */
    fun unstageFile(filePath: String): Boolean {
        val result = runGitCommand("reset", "HEAD", "--", filePath)
        return result != null
    }

    /**
     * 提交
     */
    fun commit(message: String): Boolean {
        val escapedMessage = message.replace("\"", "\\\"")
        val result = runGitCommand("commit", "-m", escapedMessage)
        return result != null
    }

    /**
     * 创建分支
     */
    fun createBranch(branchName: String, checkout: Boolean = true): Boolean {
        val result = if (checkout) {
            runGitCommand("checkout", "-b", branchName)
        } else {
            runGitCommand("branch", branchName)
        }
        return result != null
    }

    /**
     * 切换分支
     */
    fun checkout(branchName: String): Boolean {
        val result = runGitCommand("checkout", branchName)
        return result != null
    }

    /**
     * 获取状态
     */
    fun getStatus(): GitStatus {
        val output = runGitCommand("status", "--porcelain") ?: return GitStatus()
        return parseStatus(output)
    }

    /**
     * 检查是否有待提交的变更
     */
    fun hasStagedChanges(): Boolean {
        val output = runGitCommand("diff", "--cached", "--name-only") ?: return false
        return output.lines().any { it.isNotBlank() }
    }

    /**
     * 检查工作区是否有未提交的变更
     */
    fun hasUncommittedChanges(): Boolean {
        val output = runGitCommand("diff", "--name-only") ?: return false
        val unstaged = output.lines().filter { it.isNotBlank() }
        val untracked = getUntrackedFiles()
        return unstaged.isNotEmpty() || untracked.isNotEmpty()
    }

    // ==================== 内部方法 ====================

    /**
     * 运行 Git 命令
     */
    private fun runGitCommand(vararg args: String): String? {
        val dir = workingDirectory ?: return null

        return try {
            val processBuilder = ProcessBuilder("git", *args)
            processBuilder.directory(dir)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            // 设置超时
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                log.warn("Git command timed out: git ${args.joinToString(" ")}")
                return null
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                log.warn("Git command failed with exit code $exitCode: $errorOutput")
                return null
            }

            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            log.warn("Failed to run git command: ${e.message}")
            null
        }
    }

    /**
     * 解析 name-status 输出
     */
    private fun parseNameStatus(output: String): List<GitFileInfo> {
        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val statusChar = parts[0]
                    val changeType = when (statusChar.firstOrNull()) {
                        'A' -> ChangeType.ADDED
                        'M' -> ChangeType.MODIFIED
                        'D' -> ChangeType.DELETED
                        'R' -> ChangeType.RENAMED
                        'C' -> ChangeType.COPIED
                        else -> ChangeType.MODIFIED
                    }
                    GitFileInfo(
                        path = parts[1],
                        oldPath = if (parts.size > 2) parts[2] else null,
                        changeType = changeType
                    )
                } else null
            }
    }

    /**
     * 解析 status 输出
     */
    private fun parseStatus(output: String): GitStatus {
        val staged = mutableListOf<String>()
        val unstaged = mutableListOf<String>()
        val untracked = mutableListOf<String>()

        output.lines().forEach { line ->
            if (line.length >= 3) {
                val indexStatus = line[0]
                val workTreeStatus = line[1]
                val filePath = line.substring(3).trim()

                when {
                    indexStatus != ' ' && indexStatus != '?' -> staged.add(filePath)
                    workTreeStatus != ' ' -> unstaged.add(filePath)
                    indexStatus == '?' && workTreeStatus == '?' -> untracked.add(filePath)
                }
            }
        }

        return GitStatus(
            staged = staged,
            unstaged = unstaged,
            untracked = untracked
        )
    }

    // ==================== 数据类 ====================

    /**
     * Git 文件信息
     */
    data class GitFileInfo(
        val path: String,
        val oldPath: String? = null,
        val changeType: ChangeType
    )

    /**
     * 变更类型
     */
    enum class ChangeType {
        ADDED, MODIFIED, DELETED, RENAMED, COPIED
    }

    /**
     * 文件统计
     */
    data class FileStats(
        val additions: Int,
        val deletions: Int
    )

    /**
     * 提交信息
     */
    data class CommitInfo(
        val hash: String,
        val message: String,
        val authorName: String,
        val authorEmail: String,
        val date: String
    )

    /**
     * Git 状态
     */
    data class GitStatus(
        val staged: List<String> = emptyList(),
        val unstaged: List<String> = emptyList(),
        val untracked: List<String> = emptyList()
    ) {
        fun hasChanges(): Boolean = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty()
    }

    companion object {
        fun getInstance(project: Project): GitClient =
            project.getService(GitClient::class.java)
    }
}
