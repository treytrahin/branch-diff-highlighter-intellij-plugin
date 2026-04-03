package com.branchdiff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class BranchDiffStartupActivity : ProjectActivity {

    private val log = logger<BranchDiffStartupActivity>()

    override suspend fun execute(project: Project) {
        log.info("Branch Diff Highlighter starting for project: ${project.name}")

        val highlightManager = HighlightManager.getInstance(project)
        highlightManager.registerEditorListener()

        // Initial diff computation on a background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            highlightManager.refreshAll()

            val diffService = GitDiffService.getInstance(project)
            val branch = diffService.getCurrentBranch()
            val baseBranch = diffService.getBaseBranch()
            log.info("Branch Diff Highlighter active: $branch vs $baseBranch")
        }
    }
}
