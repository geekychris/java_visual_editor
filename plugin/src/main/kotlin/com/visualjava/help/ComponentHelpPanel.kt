package com.visualjava.help

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.visualjava.palette.PaletteCatalog
import com.visualjava.session.DesignerSessionService
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

/**
 * Help/docs tool window. Shows the [ComponentDoc] for whatever's currently
 * being acted on:
 *  - first preference: the canvas selection's tag (so clicking a widget on the
 *    form pops its docs);
 *  - fallback: a chooser that lists every palette entry alphabetically.
 *
 * Renders as HTML in a JEditorPane so we get clickable links to Oracle's
 * Javadoc + tutorials, code blocks for FXML examples, and tables for
 * properties/events without extra UI machinery.
 */
class ComponentHelpPanel(private val project: Project) : JBPanel<ComponentHelpPanel>(BorderLayout()) {

    private val header = JBLabel().apply { border = JBUI.Borders.empty(6, 8) }
    private val body = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) openLink(e.url?.toString() ?: e.description)
        }
    }
    private val currentTag get() = lastTag

    @Volatile private var lastTag: String? = null

    init {
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(body), BorderLayout.CENTER)

        // Whenever the canvas selection changes, refresh.
        DesignerSessionService.getInstance(project).addChangeListener { refreshFromSelection() }
        // Same when the active editor changes (so opening a fresh FXML doesn't keep stale docs).
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = refreshFromSelection()
            },
        )
        refreshFromSelection()
    }

    /** Public entry — callers (e.g. PalettePanel selection) push a tag in directly. */
    fun showFor(tagName: String) {
        lastTag = tagName
        renderCurrent()
    }

    private fun refreshFromSelection() {
        SwingUtilities.invokeLater {
            val sel = DesignerSessionService.getInstance(project).selection
            val fxId = sel.fxId
            val file = sel.file
            if (fxId == null || file == null) {
                if (lastTag == null) renderEmpty()
                return@invokeLater
            }
            // We don't have the tag on the selection record. Inspect the XML.
            val tag = com.intellij.openapi.application.ReadAction.compute<String?, RuntimeException> {
                val xml = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
                    as? com.intellij.psi.xml.XmlFile ?: return@compute null
                val root = xml.rootTag ?: return@compute null
                fun walk(t: com.intellij.psi.xml.XmlTag): com.intellij.psi.xml.XmlTag? {
                    if (t.getAttributeValue("fx:id") == fxId) return t
                    for (c in t.subTags) walk(c)?.let { return it }
                    return null
                }
                walk(root)?.localName
            }
            if (tag != null) {
                lastTag = tag
                renderCurrent()
            }
        }
    }

    private fun renderEmpty() {
        header.text = "Component docs"
        body.text = buildString {
            append("<html><body style='font-family:sans-serif; padding:8px;'>")
            append("<p>Click a widget on the canvas, or pick one from the palette, to see its docs here.</p>")
            append("<h3>All widgets</h3><ul>")
            for (d in PaletteCatalog.all.sortedBy { it.tagName }) {
                append("<li><a href='vj://show/${d.tagName}'>${d.tagName}</a></li>")
            }
            append("</ul></body></html>")
        }
        body.caretPosition = 0
    }

    private fun renderCurrent() {
        val tag = lastTag ?: return renderEmpty()
        val descriptor = PaletteCatalog.byTag(tag)
        val doc = ComponentDocsCatalog.get(tag, descriptor?.importFqn.orEmpty())
        header.text = "$tag · ${descriptor?.importFqn ?: ""}"
        body.text = renderHtml(doc)
        body.caretPosition = 0
    }

    private fun renderHtml(doc: ComponentDoc): String = buildString {
        append("<html><body style='font-family:sans-serif; padding:8px;'>")
        append("<h2>").append(escape(doc.tagName)).append("</h2>")
        append("<p>").append(escape(doc.summary)).append("</p>")

        append("<h3>FXML &nbsp; <small>[<a href='vj://copy/fxml/").append(doc.tagName).append("'>Copy</a>]</small></h3>")
        append("<pre style='background:#1e1e1e; color:#dcdcdc; padding:8px; border-radius:4px;'>")
        append(escape(doc.fxmlExample))
        append("</pre>")

        if (!doc.controllerExample.isNullOrBlank()) {
            append("<h3>Controller (Java) &nbsp; <small>[<a href='vj://copy/java/").append(doc.tagName)
                .append("'>Copy</a>] [<a href='vj://insert/").append(doc.tagName)
                .append("'>Paste into controller (/* */)</a>]</small></h3>")
            append("<pre style='background:#1e1e1e; color:#dcdcdc; padding:8px; border-radius:4px;'>")
            append(escape(doc.controllerExample))
            append("</pre>")
        }

        if (doc.commonProperties.isNotEmpty()) {
            append("<h3>Common properties</h3>")
            append("<table cellpadding='4' cellspacing='0' border='0' style='border-collapse:collapse;'>")
            for ((name, blurb) in doc.commonProperties) {
                append("<tr><td valign='top' style='padding-right:12px;'><code>")
                    .append(escape(name)).append("</code></td><td>")
                    .append(escape(blurb)).append("</td></tr>")
            }
            append("</table>")
        }

        if (doc.commonEvents.isNotEmpty()) {
            append("<h3>Common events</h3>")
            append("<table cellpadding='4' cellspacing='0' border='0'>")
            for ((name, blurb) in doc.commonEvents) {
                append("<tr><td valign='top' style='padding-right:12px;'><code>")
                    .append(escape(name)).append("</code></td><td>")
                    .append(escape(blurb)).append("</td></tr>")
            }
            append("</table>")
        }

        append("<h3>Links</h3><ul>")
        if (doc.javadocUrl.isNotBlank()) {
            append("<li><a href='").append(doc.javadocUrl).append("'>Oracle Javadoc — ").append(escape(doc.tagName)).append("</a></li>")
        }
        if (doc.tutorial != null) {
            append("<li><a href='").append(doc.tutorial).append("'>Tutorial</a></li>")
        }
        append("</ul></body></html>")
    }

    private fun openLink(url: String?) {
        url ?: return
        when {
            url.startsWith("vj://show/") -> showFor(url.removePrefix("vj://show/"))
            url.startsWith("vj://copy/fxml/") -> {
                val tag = url.removePrefix("vj://copy/fxml/")
                val descriptor = PaletteCatalog.byTag(tag)
                SampleCodeInserter.copyToClipboard(
                    ComponentDocsCatalog.get(tag, descriptor?.importFqn.orEmpty()).fxmlExample
                )
            }
            url.startsWith("vj://copy/java/") -> {
                val tag = url.removePrefix("vj://copy/java/")
                val descriptor = PaletteCatalog.byTag(tag)
                val sample = ComponentDocsCatalog.get(tag, descriptor?.importFqn.orEmpty()).controllerExample
                if (!sample.isNullOrBlank()) SampleCodeInserter.copyToClipboard(sample)
            }
            url.startsWith("vj://insert/") -> insertSampleIntoActiveController(url.removePrefix("vj://insert/"))
            else -> try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) { /* offline / no browser — ignore */ }
        }
    }

    private fun insertSampleIntoActiveController(tagName: String) {
        val file = DesignerSessionService.getInstance(project).selection.file
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: return
        val descriptor = PaletteCatalog.byTag(tagName)
        val doc = ComponentDocsCatalog.get(tagName, descriptor?.importFqn.orEmpty())
        val xmlFile = com.intellij.openapi.application.ReadAction.compute<com.intellij.psi.xml.XmlFile?, RuntimeException> {
            com.intellij.psi.PsiManager.getInstance(project).findFile(file) as? com.intellij.psi.xml.XmlFile
        } ?: return
        SampleCodeInserter.insertSampleIntoController(project, xmlFile, doc)
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
