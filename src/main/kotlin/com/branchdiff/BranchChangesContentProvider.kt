package com.branchdiff

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import javax.swing.JComponent

class BranchChangesContentProvider(private val project: Project) : ChangesViewContentProvider {

    private var panel: BranchChangesPanel? = null

    override fun initContent(): JComponent {
        val p = BranchChangesPanel(project)
        panel = p
        return p
    }

    override fun disposeContent() {
        panel = null
    }
}
