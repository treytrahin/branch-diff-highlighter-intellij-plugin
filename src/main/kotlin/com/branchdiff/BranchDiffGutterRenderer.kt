package com.branchdiff

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

class BranchDiffGutterRenderer(private val changeType: GitDiffService.ChangeType) : GutterIconRenderer() {

    override fun getIcon(): Icon = when (changeType) {
        GitDiffService.ChangeType.ADDED -> ADDED_ICON
        else -> MODIFIED_ICON
    }

    override fun getTooltipText(): String = when (changeType) {
        GitDiffService.ChangeType.ADDED -> "Added in branch"
        else -> "Modified in branch"
    }

    override fun equals(other: Any?): Boolean =
        other is BranchDiffGutterRenderer && other.changeType == changeType

    override fun hashCode(): Int = changeType.hashCode()

    companion object {
        private val ADDED_ICON = BarIcon(JBColor(Color(0x2E, 0x7D, 0x32), Color(0x4C, 0xAF, 0x50)))
        private val MODIFIED_ICON = BarIcon(JBColor(Color(0x15, 0x65, 0xC0), Color(0x42, 0xA5, 0xF5)))
    }

    /**
     * A thin colored bar icon rendered in the gutter to indicate change type.
     */
    private class BarIcon(private val color: Color) : Icon {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillRoundRect(x, y, iconWidth, iconHeight, 2, 2)
            g2.dispose()
        }

        override fun getIconWidth(): Int = 3
        override fun getIconHeight(): Int = 12
    }
}
