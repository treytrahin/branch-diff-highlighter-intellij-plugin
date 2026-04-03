package com.branchdiff

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import git4idea.GitUtil
import java.util.function.Predicate

class BranchChangesVisibilityPredicate : Predicate<Project> {

    override fun test(project: Project): Boolean {
        // Show the tab whenever the project has a git repository
        return GitUtil.getRepositoryManager(project).repositories.isNotEmpty()
    }
}
