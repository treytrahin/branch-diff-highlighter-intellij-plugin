package com.branchdiff

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import java.io.BufferedReader

/**
 * Runs git diff against the base branch and parses changed line ranges per file.
 */
@Service(Service.Level.PROJECT)
class GitDiffService(private val project: Project) {

    private val log = logger<GitDiffService>()

    data class LineRange(val startLine: Int, val lineCount: Int)

    enum class ChangeType { ADDED, MODIFIED, DELETED, RENAMED }

    data class ChangedFile(val path: String, val type: ChangeType, val oldPath: String? = null)

    data class ChangedLine(val line: Int, val type: ChangeType)

    /**
     * Returns the list of files changed on this branch compared to the base branch.
     */
    fun getChangedFiles(): List<ChangedFile> {
        val repoRoot = findRepoRoot() ?: return emptyList()
        val baseBranch = detectBaseBranch(repoRoot) ?: return emptyList()

        val output = runGitCommand(repoRoot, "diff", "$baseBranch...HEAD", "--name-status", "--no-color")
            ?: return emptyList()

        return DiffParser.parseNameStatusOutput(output)
    }

    fun getRepoRoot(): String? = findRepoRoot()

    /**
     * Returns a map of (repo-relative file path) -> list of changed line numbers.
     */
    fun getChangedLines(): Map<String, List<ChangedLine>> {
        val repoRoot = findRepoRoot() ?: return emptyMap()
        val baseBranch = detectBaseBranch(repoRoot) ?: return emptyMap()

        log.info("Computing diff against base branch: $baseBranch")

        val diffOutput = runGitCommand(repoRoot, "diff", "$baseBranch...HEAD", "--unified=0", "--no-color")
            ?: return emptyMap()

        return DiffParser.parseDiffOutput(diffOutput)
    }

    /**
     * Returns the current branch name.
     */
    fun getCurrentBranch(): String? {
        val repoRoot = findRepoRoot() ?: return null
        return runGitCommand(repoRoot, "rev-parse", "--abbrev-ref", "HEAD")?.trim()
    }

    /**
     * Returns the detected base branch name.
     */
    fun getBaseBranch(): String? {
        val repoRoot = findRepoRoot() ?: return null
        return detectBaseBranch(repoRoot)
    }

    private fun findRepoRoot(): String? {
        val repositories = GitUtil.getRepositoryManager(project).repositories
        if (repositories.isEmpty()) {
            log.warn("No git repositories found in project")
            return null
        }
        return repositories.first().root.path
    }

    private fun detectBaseBranch(repoRoot: String): String? {
        // Check common base branch names
        for (candidate in listOf("main", "master", "develop")) {
            val result = runGitCommand(repoRoot, "rev-parse", "--verify", "origin/$candidate")
            if (result != null && result.isNotBlank()) {
                return "origin/$candidate"
            }
        }

        // Fallback: try without origin/ prefix
        for (candidate in listOf("main", "master", "develop")) {
            val result = runGitCommand(repoRoot, "rev-parse", "--verify", candidate)
            if (result != null && result.isNotBlank()) {
                return candidate
            }
        }

        log.warn("Could not detect base branch")
        return null
    }

    private fun runGitCommand(repoRoot: String, vararg args: String): String? {
        return try {
            val command = listOf("git", "-C", repoRoot) + args.toList()
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()

            if (exitCode == 0) output else null
        } catch (e: Exception) {
            log.warn("Git command failed: git ${args.joinToString(" ")}", e)
            null
        }
    }

    companion object {
        fun getInstance(project: Project): GitDiffService =
            project.getService(GitDiffService::class.java)
    }
}
