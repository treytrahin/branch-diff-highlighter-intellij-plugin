package com.branchdiff

import com.branchdiff.GitDiffService.ChangeType
import com.branchdiff.GitDiffService.ChangedFile
import com.branchdiff.GitDiffService.ChangedLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DiffParserTest {

    @Nested
    inner class ParseDiffOutput {

        @Test
        fun `parses single file with added lines`() {
            val diff = """
                diff --git a/src/main/File.kt b/src/main/File.kt
                new file mode 100644
                --- /dev/null
                +++ b/src/main/File.kt
                @@ -0,0 +1,5 @@
                +line 1
                +line 2
                +line 3
                +line 4
                +line 5
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            assertEquals(1, result.size)
            val lines = result["src/main/File.kt"]!!
            assertEquals(5, lines.size)
            assertEquals(ChangedLine(1, ChangeType.ADDED), lines[0])
            assertEquals(ChangedLine(5, ChangeType.ADDED), lines[4])
        }

        @Test
        fun `parses single file with modified lines`() {
            val diff = """
                diff --git a/src/main/File.kt b/src/main/File.kt
                --- a/src/main/File.kt
                +++ b/src/main/File.kt
                @@ -10,3 +10,3 @@
                +modified line 1
                +modified line 2
                +modified line 3
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            val lines = result["src/main/File.kt"]!!
            assertEquals(3, lines.size)
            assertTrue(lines.all { it.type == ChangeType.MODIFIED })
            assertEquals(10, lines[0].line)
            assertEquals(12, lines[2].line)
        }

        @Test
        fun `parses multiple files`() {
            val diff = """
                diff --git a/file1.kt b/file1.kt
                --- a/file1.kt
                +++ b/file1.kt
                @@ -0,0 +1,2 @@
                +new line 1
                +new line 2
                diff --git a/file2.kt b/file2.kt
                --- a/file2.kt
                +++ b/file2.kt
                @@ -5,1 +5,1 @@
                +changed line
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            assertEquals(2, result.size)
            assertEquals(2, result["file1.kt"]!!.size)
            assertEquals(1, result["file2.kt"]!!.size)
        }

        @Test
        fun `parses multiple hunks in same file`() {
            val diff = """
                diff --git a/file.kt b/file.kt
                --- a/file.kt
                +++ b/file.kt
                @@ -0,0 +3,2 @@
                +added line 1
                +added line 2
                @@ -20,1 +22,1 @@
                +modified line
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            val lines = result["file.kt"]!!
            assertEquals(3, lines.size)
            assertEquals(ChangedLine(3, ChangeType.ADDED), lines[0])
            assertEquals(ChangedLine(4, ChangeType.ADDED), lines[1])
            assertEquals(ChangedLine(22, ChangeType.MODIFIED), lines[2])
        }

        @Test
        fun `handles hunk with no count meaning single line`() {
            val diff = """
                diff --git a/file.kt b/file.kt
                --- a/file.kt
                +++ b/file.kt
                @@ -5 +5 @@
                +single modified line
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            val lines = result["file.kt"]!!
            assertEquals(1, lines.size)
            assertEquals(ChangedLine(5, ChangeType.MODIFIED), lines[0])
        }

        @Test
        fun `handles hunk header with trailing context`() {
            val diff = """
                diff --git a/file.kt b/file.kt
                --- a/file.kt
                +++ b/file.kt
                @@ -10,2 +10,3 @@ fun someFunction() {
                +added inside function
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)

            val lines = result["file.kt"]!!
            assertEquals(3, lines.size)
            assertEquals(10, lines[0].line)
        }

        @Test
        fun `returns empty map for empty input`() {
            val result = DiffParser.parseDiffOutput("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns empty map for input with no hunks`() {
            val diff = """
                diff --git a/file.kt b/file.kt
                --- a/file.kt
                +++ b/file.kt
            """.trimIndent()

            val result = DiffParser.parseDiffOutput(diff)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `ignores hunk lines before any file header`() {
            val diff = "@@ -1,2 +1,2 @@\n+orphan line"
            val result = DiffParser.parseDiffOutput(diff)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ParseNameStatusOutput {

        @Test
        fun `parses added file`() {
            val output = "A\tsrc/main/NewFile.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(1, result.size)
            assertEquals(ChangedFile("src/main/NewFile.kt", ChangeType.ADDED), result[0])
        }

        @Test
        fun `parses modified file`() {
            val output = "M\tsrc/main/Existing.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(ChangedFile("src/main/Existing.kt", ChangeType.MODIFIED), result[0])
        }

        @Test
        fun `parses deleted file`() {
            val output = "D\tsrc/main/Removed.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(ChangedFile("src/main/Removed.kt", ChangeType.DELETED), result[0])
        }

        @Test
        fun `parses renamed file with old path`() {
            val output = "R100\tsrc/old/File.kt\tsrc/new/File.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(1, result.size)
            assertEquals("src/new/File.kt", result[0].path)
            assertEquals(ChangeType.RENAMED, result[0].type)
            assertEquals("src/old/File.kt", result[0].oldPath)
        }

        @Test
        fun `parses multiple files`() {
            val output = """
                A	src/NewFile.kt
                M	src/Modified.kt
                D	src/Deleted.kt
                M	src/AnotherModified.kt
            """.trimIndent()

            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(4, result.size)
            assertEquals(ChangeType.ADDED, result[0].type)
            assertEquals(ChangeType.MODIFIED, result[1].type)
            assertEquals(ChangeType.DELETED, result[2].type)
            assertEquals(ChangeType.MODIFIED, result[3].type)
        }

        @Test
        fun `treats unknown status codes as modified`() {
            val output = "C\tsrc/Copied.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(ChangeType.MODIFIED, result[0].type)
        }

        @Test
        fun `returns empty list for empty input`() {
            val result = DiffParser.parseNameStatusOutput("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `skips malformed lines`() {
            val output = """
                A	src/Good.kt
                bad line no tab
                M	src/AlsoGood.kt
            """.trimIndent()

            val result = DiffParser.parseNameStatusOutput(output)

            assertEquals(2, result.size)
            assertEquals("src/Good.kt", result[0].path)
            assertEquals("src/AlsoGood.kt", result[1].path)
        }

        @Test
        fun `added file has null oldPath`() {
            val output = "A\tsrc/New.kt"
            val result = DiffParser.parseNameStatusOutput(output)

            assertNull(result[0].oldPath)
        }
    }
}
