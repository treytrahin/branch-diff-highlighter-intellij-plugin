package com.branchdiff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.vcsUtil.VcsUtil
import java.awt.BorderLayout
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class BranchChangesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = logger<BranchChangesPanel>()
    private val changesBrowser = SimpleChangesBrowser(project, false, false)
    private val statusLabel = JLabel("Loading...", SwingConstants.CENTER)

    init {
        add(changesBrowser, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        refresh()
    }

    fun refresh() {
        statusLabel.text = "Computing diff..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val diffService = GitDiffService.getInstance(project)
            val changedFiles = diffService.getChangedFiles()
            val repoRoot = diffService.getRepoRoot()
            val currentBranch = diffService.getCurrentBranch() ?: "unknown"
            val baseBranch = diffService.getBaseBranch() ?: "unknown"

            val changes = changedFiles.mapNotNull { changedFile ->
                toChange(repoRoot, changedFile)
            }

            ApplicationManager.getApplication().invokeLater {
                changesBrowser.setChangesToDisplay(changes)

                statusLabel.text = if (changes.isEmpty()) {
                    "No changes on $currentBranch vs $baseBranch"
                } else {
                    "${changes.size} files changed on $currentBranch vs $baseBranch"
                }
            }
        }
    }

    private fun toChange(repoRoot: String?, changedFile: GitDiffService.ChangedFile): Change? {
        if (repoRoot == null) return null

        val filePath = VcsUtil.getFilePath(File(repoRoot, changedFile.path).absolutePath, false)
        val fileStatus = when (changedFile.type) {
            GitDiffService.ChangeType.ADDED -> FileStatus.ADDED
            GitDiffService.ChangeType.MODIFIED -> FileStatus.MODIFIED
            GitDiffService.ChangeType.DELETED -> FileStatus.DELETED
            GitDiffService.ChangeType.RENAMED -> FileStatus.MODIFIED
        }

        val beforeRevision = when (changedFile.type) {
            GitDiffService.ChangeType.ADDED -> null
            GitDiffService.ChangeType.RENAMED -> {
                val oldFilePath = VcsUtil.getFilePath(
                    File(repoRoot, changedFile.oldPath ?: changedFile.path).absolutePath,
                    false,
                )
                CurrentContentRevision.create(oldFilePath)
            }
            else -> CurrentContentRevision.create(filePath)
        }

        val afterRevision = when (changedFile.type) {
            GitDiffService.ChangeType.DELETED -> null
            else -> CurrentContentRevision.create(filePath)
        }

        return Change(beforeRevision, afterRevision, fileStatus)
    }
}
