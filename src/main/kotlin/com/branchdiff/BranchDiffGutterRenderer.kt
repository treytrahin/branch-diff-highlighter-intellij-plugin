package com.branchdiff

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

class BranchDiffGutterRenderer(
    private val changeType: GitDiffService.ChangeType,
    private val hasNeighborAbove: Boolean,
    private val hasNeighborBelow: Boolean,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = when (changeType) {
        GitDiffService.ChangeType.ADDED -> BarIcon(ADDED_COLOR, hasNeighborAbove, hasNeighborBelow)
        else -> BarIcon(MODIFIED_COLOR, hasNeighborAbove, hasNeighborBelow)
    }

    override fun getTooltipText(): String = when (changeType) {
        GitDiffService.ChangeType.ADDED -> "Added in branch"
        else -> "Modified in branch"
    }

    override fun equals(other: Any?): Boolean =
        other is BranchDiffGutterRenderer &&
            other.changeType == changeType &&
            other.hasNeighborAbove == hasNeighborAbove &&
            other.hasNeighborBelow == hasNeighborBelow

    override fun hashCode(): Int {
        var result = changeType.hashCode()
        result = 31 * result + hasNeighborAbove.hashCode()
        result = 31 * result + hasNeighborBelow.hashCode()
        return result
    }

    companion object {
        private val ADDED_COLOR = JBColor(Color(0x2E, 0x7D, 0x32), Color(0x4C, 0xAF, 0x50))
        private val MODIFIED_COLOR = JBColor(Color(0x15, 0x65, 0xC0), Color(0x42, 0xA5, 0xF5))
    }

    private class BarIcon(
        private val color: Color,
        private val hasNeighborAbove: Boolean,
        private val hasNeighborBelow: Boolean,
    ) : Icon {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color

            // Extend painting beyond the icon bounds to connect with neighbors
            val topExtend = if (hasNeighborAbove) 4 else 0
            val bottomExtend = if (hasNeighborBelow) 4 else 0
            val drawY = y - topExtend
            val drawHeight = iconHeight + topExtend + bottomExtend

            val arcSize = 2
            if (hasNeighborAbove && hasNeighborBelow) {
                // Middle of a run — no rounding
                g2.fillRect(x, drawY, iconWidth, drawHeight)
            } else if (hasNeighborAbove) {
                // Bottom of a run — round only bottom corners
                g2.fillRect(x, drawY, iconWidth, drawHeight - arcSize)
                g2.fillRoundRect(x, drawY, iconWidth, drawHeight, arcSize, arcSize)
            } else if (hasNeighborBelow) {
                // Top of a run — round only top corners
                g2.fillRect(x, drawY + arcSize, iconWidth, drawHeight - arcSize)
                g2.fillRoundRect(x, drawY, iconWidth, drawHeight, arcSize, arcSize)
            } else {
                // Isolated line — fully rounded
                g2.fillRoundRect(x, drawY, iconWidth, drawHeight, arcSize, arcSize)
            }

            g2.dispose()
        }

        override fun getIconWidth(): Int = 3
        override fun getIconHeight(): Int = 12
    }
}
