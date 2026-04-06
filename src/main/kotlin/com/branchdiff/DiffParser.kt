package com.branchdiff

import com.branchdiff.GitDiffService.ChangeType
import com.branchdiff.GitDiffService.ChangedFile
import com.branchdiff.GitDiffService.ChangedLine

object DiffParser {

    // Matches @@ -old,oldCount +newStart,newCount @@ or @@ -old +newStart,newCount @@
    private val HUNK_PATTERN = Regex("""@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

    fun parseDiffOutput(output: String): Map<String, List<ChangedLine>> {
        val result = mutableMapOf<String, MutableList<ChangedLine>>()
        var currentFile: String? = null

        for (line in output.lines()) {
            if (line.startsWith("+++ b/")) {
                currentFile = line.removePrefix("+++ b/")
                continue
            }

            if (line.startsWith("@@") && currentFile != null) {
                val hunkMatch = HUNK_PATTERN.find(line) ?: continue
                val oldCount = hunkMatch.groupValues[1].toIntOrNull() ?: 1
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

    fun parseNameStatusOutput(output: String): List<ChangedFile> {
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
}
