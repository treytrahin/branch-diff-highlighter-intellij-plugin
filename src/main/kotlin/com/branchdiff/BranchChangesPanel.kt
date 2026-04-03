package com.branchdiff

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class BranchChangesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = logger<BranchChangesPanel>()
    private val rootNode = DefaultMutableTreeNode("Branch Changes")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val statusLabel = JLabel("Loading...", SwingConstants.CENTER)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = ChangedFileCellRenderer()

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedFile()
                }
            }
        })

        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        refresh()
    }

    private fun createToolbar(): JPanel {
        val group = DefaultActionGroup()

        group.add(object : AnAction("Refresh", "Refresh branch changes", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                refresh()
            }
        })

        val toolbar = ActionManager.getInstance().createActionToolbar("BranchChanges", group, true)
        toolbar.targetComponent = this
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.add(toolbar.component, BorderLayout.WEST)
        return toolbarPanel
    }

    fun refresh() {
        statusLabel.text = "Computing diff..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val diffService = GitDiffService.getInstance(project)
            val changedFiles = diffService.getChangedFiles()
            val currentBranch = diffService.getCurrentBranch() ?: "unknown"
            val baseBranch = diffService.getBaseBranch() ?: "unknown"

            ApplicationManager.getApplication().invokeLater {
                rootNode.removeAllChildren()

                if (changedFiles.isEmpty()) {
                    statusLabel.text = "No changes on $currentBranch vs $baseBranch"
                    treeModel.reload()
                    return@invokeLater
                }

                // Group files by directory
                val byDirectory = changedFiles.groupBy { file ->
                    val lastSlash = file.path.lastIndexOf('/')
                    if (lastSlash >= 0) file.path.substring(0, lastSlash) else ""
                }.toSortedMap()

                for ((dir, files) in byDirectory) {
                    val dirNode = DefaultMutableTreeNode(DirectoryEntry(dir, files.size))
                    for (file in files.sortedBy { it.path }) {
                        dirNode.add(DefaultMutableTreeNode(file))
                    }
                    rootNode.add(dirNode)
                }

                treeModel.reload()

                // Expand all nodes
                for (i in 0 until tree.rowCount) {
                    tree.expandRow(i)
                }

                statusLabel.text = "${changedFiles.size} files changed on $currentBranch vs $baseBranch"
            }
        }
    }

    private fun openSelectedFile() {
        val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val changedFile = selectedNode.userObject as? GitDiffService.ChangedFile ?: return

        val repoRoot = GitDiffService.getInstance(project).getRepoRoot() ?: return
        val fullPath = File(repoRoot, changedFile.path).absolutePath
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(fullPath) ?: return

        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    data class DirectoryEntry(val path: String, val fileCount: Int)

    private class ChangedFileCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = value as? DefaultMutableTreeNode ?: return

            when (val obj = node.userObject) {
                is GitDiffService.ChangedFile -> {
                    val fileName = obj.path.substringAfterLast('/')
                    icon = when (obj.type) {
                        GitDiffService.ChangeType.ADDED -> AllIcons.General.Add
                        GitDiffService.ChangeType.MODIFIED -> AllIcons.Actions.Edit
                        GitDiffService.ChangeType.DELETED -> AllIcons.General.Remove
                        GitDiffService.ChangeType.RENAMED -> AllIcons.Actions.Forward
                    }

                    val textColor = when (obj.type) {
                        GitDiffService.ChangeType.ADDED -> JBColor(0x2E7D32, 0x4CAF50)
                        GitDiffService.ChangeType.MODIFIED -> JBColor(0x1565C0, 0x42A5F5)
                        GitDiffService.ChangeType.DELETED -> JBColor(0xC62828, 0xEF5350)
                        GitDiffService.ChangeType.RENAMED -> JBColor(0x6A1B9A, 0xBA68C8)
                    }

                    append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, textColor))

                    if (obj.type == GitDiffService.ChangeType.RENAMED && obj.oldPath != null) {
                        append(
                            "  ${obj.oldPath.substringAfterLast('/')}",
                            SimpleTextAttributes.GRAYED_ATTRIBUTES,
                        )
                    }
                }

                is DirectoryEntry -> {
                    icon = AllIcons.Nodes.Folder
                    val displayPath = obj.path.ifEmpty { "(root)" }
                    append(displayPath, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${obj.fileCount} files", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
    }
}
