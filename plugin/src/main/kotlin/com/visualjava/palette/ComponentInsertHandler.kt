package com.visualjava.palette

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * Inserts a palette [ComponentDescriptor] into an FXML document at the given
 * (layoutX, layoutY) in FXML coordinate space.
 *
 * Behaviour:
 *  - Ensures the corresponding `<?import ... ?>` declaration exists.
 *  - Generates a fresh fx:id (e.g., `button1`, `button2` …).
 *  - Sets layoutX / layoutY on the new element.
 *  - Inserts under the root tag's `<children>` collection (creating it if absent).
 *
 * All mutations run inside a single [WriteCommandAction] for atomic undo.
 */
class ComponentInsertHandler(private val project: Project) {

    fun insert(
        fxmlFile: XmlFile,
        descriptor: ComponentDescriptor,
        layoutX: Double,
        layoutY: Double,
        parentFxId: String? = null,
        dropInfo: ContainerDropInfo? = null,
    ): String? {
        var newFxId: String? = null
        WriteCommandAction.runWriteCommandAction(
            project,
            "Add ${descriptor.displayName}",
            null,
            {
                ensureImport(fxmlFile, descriptor.importFqn)
                for (extra in descriptor.extraImports) ensureImport(fxmlFile, extra)
                dropInfo?.wrapImport?.let { ensureImport(fxmlFile, it) }

                val root = fxmlFile.rootTag ?: return@runWriteCommandAction
                val parent = parentFxId?.let { findTagByFxId(fxmlFile, it) } ?: root

                // For non-children slots (BorderPane.top, ScrollPane.content, …)
                // we suppress layoutX/Y — those make no sense in non-Pane layouts.
                val info = dropInfo ?: ContainerDropInfo("children", collection = true)
                val suppressLayout = info.slot != "children"
                val fxId = nextFxId(fxmlFile, descriptor.tagName)
                val factory = XmlElementFactory.getInstance(project)
                val attrs = buildAttrString(fxId, layoutX, layoutY, descriptor.defaultAttrs, suppressLayout)
                val body = descriptor.bodyXml
                val inner = if (body.isNullOrBlank()) {
                    "<${descriptor.tagName}$attrs />"
                } else {
                    "<${descriptor.tagName}$attrs>$body</${descriptor.tagName}>"
                }
                val xml = if (info.wrapTagName != null) {
                    val wrapAttrs = info.wrapAttrs.entries.joinToString("") { (k, v) -> """ $k="$v"""" }
                    // The wrapper holds the dropped node in its <content> slot for
                    // Tab/TitledPane — both expose content as a single child.
                    "<${info.wrapTagName}$wrapAttrs><content>$inner</content></${info.wrapTagName}>"
                } else inner

                val newTag = factory.createTagFromText(xml)
                val slot = parent.findFirstSubTag(info.slot) ?: createSlot(parent, info.slot)
                if (info.collection) {
                    slot.addSubTag(newTag, false)
                } else {
                    // Singleton slot: replace any existing child.
                    for (existing in slot.subTags) existing.delete()
                    slot.addSubTag(newTag, false)
                }
                newFxId = fxId
            },
        )
        return newFxId
    }

    private fun findTagByFxId(fxmlFile: XmlFile, fxId: String): XmlTag? {
        val root = fxmlFile.rootTag ?: return null
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == fxId }
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }

    private fun createSlot(root: XmlTag, slotName: String): XmlTag {
        val factory = XmlElementFactory.getInstance(project)
        val empty = factory.createTagFromText("<$slotName/>")
        return root.addSubTag(empty, false)
    }

    private fun buildAttrString(
        fxId: String,
        x: Double,
        y: Double,
        extra: Map<String, String>,
        suppressLayout: Boolean = false,
    ): String {
        val attrs = linkedMapOf<String, String>("fx:id" to fxId)
        if (!suppressLayout) {
            attrs["layoutX"] = x.toInt().toString()
            attrs["layoutY"] = y.toInt().toString()
        }
        attrs.putAll(extra)
        return attrs.entries.joinToString(separator = "") { (k, v) -> """ $k="$v"""" }
    }

    private fun nextFxId(file: XmlFile, tagName: String): String {
        val prefix = tagName.replaceFirstChar { it.lowercase() }
        val used = mutableSetOf<String>()
        file.rootTag?.let { collectFxIds(it, used) }
        var n = 1
        while ("$prefix$n" in used) n++
        return "$prefix$n"
    }

    private fun collectFxIds(tag: XmlTag, out: MutableSet<String>) {
        tag.getAttributeValue("fx:id")?.let { out += it }
        for (child in tag.subTags) collectFxIds(child, out)
    }

    private fun ensureImport(fxmlFile: XmlFile, fqn: String) {
        val needle = "<?import $fqn?>"
        val document = PsiDocumentManager.getInstance(project).getDocument(fxmlFile) ?: return
        val text = document.text
        if (text.contains(needle)) return

        val regex = Regex("<\\?import [^?]+\\?>")
        val matches = regex.findAll(text).toList()
        val insertAt = if (matches.isNotEmpty()) {
            matches.last().range.last + 1
        } else {
            val declEnd = text.indexOf("?>", text.indexOf("<?xml"))
            if (declEnd >= 0) declEnd + 2 else 0
        }
        document.insertString(insertAt, "\n$needle")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
