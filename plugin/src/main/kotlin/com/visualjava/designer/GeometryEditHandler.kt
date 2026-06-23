package com.visualjava.designer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * Commits a batch of move/resize updates to the FXML PSI as one undo step.
 * One entry per affected fx:id.
 */
class GeometryEditHandler(private val project: Project) {

    fun commit(fxmlFile: XmlFile, updates: List<DesignCanvasPanel.GeometryUpdate>) {
        if (updates.isEmpty()) return
        val title = if (updates.size == 1) "Move / resize ${updates[0].fxId}" else "Move ${updates.size} components"
        WriteCommandAction.runWriteCommandAction(project, title, null, {
            val byFxId = collectTags(fxmlFile).associateBy { it.getAttributeValue("fx:id") }
            for (u in updates) {
                val tag = byFxId[u.fxId] ?: continue
                tag.setAttribute("layoutX", u.x.toString())
                tag.setAttribute("layoutY", u.y.toString())
                tag.setAttribute("prefWidth", u.w.toString())
                tag.setAttribute("prefHeight", u.h.toString())
            }
        })
    }

    private fun collectTags(fxmlFile: XmlFile): List<XmlTag> {
        val root = fxmlFile.rootTag ?: return emptyList()
        return walk(root).toList()
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }
}
