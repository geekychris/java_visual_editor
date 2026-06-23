package com.visualjava.palette

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.content.ContentFactory
import com.visualjava.outline.FormOutlinePanel

class PaletteToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val splitter = JBSplitter(/* vertical = */ true, /* proportion = */ 0.65f).apply {
            firstComponent = PalettePanel(project).apply {
                border = IdeBorderFactory.createTitledBorder("Components")
            }
            secondComponent = FormOutlinePanel(project).apply {
                border = IdeBorderFactory.createTitledBorder("Form Outline")
            }
        }
        val content = ContentFactory.getInstance().createContent(splitter, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
