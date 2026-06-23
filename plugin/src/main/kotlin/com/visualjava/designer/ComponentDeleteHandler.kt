package com.visualjava.designer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/** Removes one or more components (by fx:id) from the FXML in a single undo step. */
class ComponentDeleteHandler(private val project: Project) {

    fun delete(fxmlFile: XmlFile, fxIds: List<String>) {
        if (fxIds.isEmpty()) return
        val title = if (fxIds.size == 1) "Delete ${fxIds[0]}" else "Delete ${fxIds.size} components"
        WriteCommandAction.runWriteCommandAction(project, title, null, {
            val byFxId = collect(fxmlFile).associateBy { it.getAttributeValue("fx:id") }
            for (id in fxIds) byFxId[id]?.delete()
        })
    }

    private fun collect(fxmlFile: XmlFile): List<XmlTag> {
        val root = fxmlFile.rootTag ?: return emptyList()
        return walk(root).toList()
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }
}
