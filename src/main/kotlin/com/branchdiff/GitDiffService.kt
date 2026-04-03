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

        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 2) return@mapNotNull null

                val statusCode = parts[0].first()
                val path = parts.last()
                val oldPath = if (parts.size == 3) parts[1] else null

                val type = when (statusCode) {
                    'A' -> ChangeType.ADDED
                    'M' -> ChangeType.MODIFIED
                    'D' -> ChangeType.DELETED
                    'R' -> ChangeType.RENAMED
                    else -> ChangeType.MODIFIED
                }

                ChangedFile(path, type, oldPath)
            }
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

        return parseDiffOutput(diffOutput)
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

    private fun parseDiffOutput(output: String): Map<String, List<ChangedLine>> {
        val result = mutableMapOf<String, MutableList<ChangedLine>>()
        var currentFile: String? = null

        for (line in output.lines()) {
            // Detect file being diffed: +++ b/path/to/file
            if (line.startsWith("+++ b/")) {
                currentFile = line.removePrefix("+++ b/")
                continue
            }

            // Parse hunk headers: @@ -oldStart,oldCount +newStart,newCount @@
            if (line.startsWith("@@") && currentFile != null) {
                val hunkMatch = HUNK_PATTERN.find(line) ?: continue
                val oldCount = hunkMatch.groupValues[1].toIntOrNull() ?: 0
                val newStart = hunkMatch.groupValues[2].toIntOrNull() ?: continue
                val newCount = hunkMatch.groupValues[3].toIntOrNull() ?: 1

                val changeType = if (oldCount == 0) ChangeType.ADDED else ChangeType.MODIFIED
                val fileChanges = result.getOrPut(currentFile) { mutableListOf() }

                for (i in 0 until newCount) {
                    fileChanges.add(ChangedLine(newStart + i, changeType))
                }
            }
        }

        return result
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
        // Matches @@ -old,oldCount +newStart,newCount @@ or @@ -old +newStart,newCount @@
        // Also handles cases like @@ -0,0 +1,5 @@ or @@ -5 +5,2 @@
        private val HUNK_PATTERN = Regex("""@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

        fun getInstance(project: Project): GitDiffService =
            project.getService(GitDiffService::class.java)
    }
}
