package com.branchdiff

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content

class BranchChangesContentPreloader(private val project: Project) : ChangesViewContentProvider.Preloader {

    override fun preloadTabContent(content: Content) {
        // Pre-compute diff in background so tab loads fast
        GitDiffService.getInstance(project).getChangedFiles()
    }
}
