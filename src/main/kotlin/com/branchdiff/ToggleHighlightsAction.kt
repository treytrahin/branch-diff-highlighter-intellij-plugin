package com.branchdiff

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleHighlightsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        HighlightManager.getInstance(project).toggle()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project != null) {
            val enabled = HighlightManager.getInstance(project).enabled
            e.presentation.text = if (enabled) "Disable Branch Diff Highlights" else "Enable Branch Diff Highlights"
        }
    }
}
