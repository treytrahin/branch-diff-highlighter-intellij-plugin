package com.branchdiff

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import git4idea.GitUtil
import java.awt.Color
import java.awt.Font

@Service(Service.Level.PROJECT)
class HighlightManager(private val project: Project) : Disposable {

    private val log = logger<HighlightManager>()

    // Cache of changed lines per repo-relative path
    private var changedLinesCache: Map<String, List<GitDiffService.ChangedLine>> = emptyMap()

    // Track highlighters so we can remove them
    private val activeHighlighters = mutableMapOf<Editor, List<RangeHighlighter>>()

    var enabled: Boolean = true
        private set

    fun toggle() {
        enabled = !enabled
        if (enabled) {
            refreshAll()
        } else {
            clearAll()
        }
    }

    fun refreshAll() {
        // Recompute diff
        changedLinesCache = GitDiffService.getInstance(project).getChangedLines()
        log.info("Diff computed: ${changedLinesCache.size} files with changes")

        // Clear old highlights
        clearAll()

        if (!enabled) return

        // Apply to all open editors
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project != project) continue
            applyHighlights(editor)
        }
    }

    fun applyHighlights(editor: Editor) {
        if (!enabled) return

        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val relativePath = getRepoRelativePath(virtualFile) ?: return
        val changedLines = changedLinesCache[relativePath] ?: return

        val document = editor.document
        val markupModel = editor.markupModel
        val highlighters = mutableListOf<RangeHighlighter>()

        for (change in changedLines) {
            val lineIndex = change.line - 1 // Git lines are 1-based, editor is 0-based
            if (lineIndex < 0 || lineIndex >= document.lineCount) continue

            val startOffset = document.getLineStartOffset(lineIndex)
            val endOffset = document.getLineEndOffset(lineIndex)

            val attributes = when (change.type) {
                GitDiffService.ChangeType.ADDED -> ADDED_ATTRIBUTES
                else -> MODIFIED_ATTRIBUTES
            }

            val highlighter = markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.LAST + 1,
                attributes,
                HighlighterTargetArea.LINES_IN_RANGE
            )

            highlighter.gutterIconRenderer = BranchDiffGutterRenderer(change.type)
            highlighters.add(highlighter)
        }

        if (highlighters.isNotEmpty()) {
            activeHighlighters[editor] = highlighters
            log.info("Applied ${highlighters.size} highlights to $relativePath")
        }
    }

    fun removeHighlights(editor: Editor) {
        activeHighlighters.remove(editor)?.forEach { highlighter ->
            if (highlighter.isValid) {
                editor.markupModel.removeHighlighter(highlighter)
            }
        }
    }

    private fun clearAll() {
        for ((editor, highlighters) in activeHighlighters) {
            for (highlighter in highlighters) {
                if (highlighter.isValid) {
                    editor.markupModel.removeHighlighter(highlighter)
                }
            }
        }
        activeHighlighters.clear()
    }

    private fun getRepoRelativePath(file: VirtualFile): String? {
        val repositories = GitUtil.getRepositoryManager(project).repositories
        for (repo in repositories) {
            val rootPath = repo.root.path
            val filePath = file.path
            if (filePath.startsWith(rootPath)) {
                return filePath.removePrefix("$rootPath/")
            }
        }
        return null
    }

    fun registerEditorListener() {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != project) return
                applyHighlights(editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != project) return
                activeHighlighters.remove(editor)
            }
        }, this@HighlightManager)
    }

    override fun dispose() {
        clearAll()
    }

    companion object {
        private val ADDED_ATTRIBUTES = TextAttributes().apply {
            backgroundColor = JBColor(
                Color(0x2E, 0x7D, 0x32, 0x30), // Light theme: green tint
                Color(0x2E, 0x7D, 0x32, 0x40)  // Dark theme: green tint
            )
        }

        private val MODIFIED_ATTRIBUTES = TextAttributes().apply {
            backgroundColor = JBColor(
                Color(0x15, 0x65, 0xC0, 0x30), // Light theme: blue tint
                Color(0x15, 0x65, 0xC0, 0x40)  // Dark theme: blue tint
            )
        }

        fun getInstance(project: Project): HighlightManager =
            project.getService(HighlightManager::class.java)
    }
}
