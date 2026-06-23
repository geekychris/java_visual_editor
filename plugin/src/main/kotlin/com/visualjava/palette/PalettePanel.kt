package com.visualjava.palette

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler

/** Left-side tool window: categorised list of widgets with drag enabled. */
class PalettePanel(private val project: Project? = null) : JBPanel<PalettePanel>(BorderLayout()) {

    private val listModel = DefaultListModel<PaletteEntry>()
    private val list = JBList(listModel).apply {
        dragEnabled = true
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = EntryRenderer()
    }.also { jbl ->
        // Live tooltip per row: short summary + minimal FXML snippet. Falls back
        // to a "no curated docs" stub for widgets without an explicit entry.
        jbl.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val idx = jbl.locationToIndex(e.point)
                val tip = (jbl.model.getElementAt(idx) as? PaletteEntry.Item)
                    ?.let { tooltipFor(it.descriptor) }
                if (jbl.toolTipText != tip) jbl.toolTipText = tip
            }
        })
    }

    private fun tooltipFor(d: ComponentDescriptor): String {
        val doc = com.visualjava.help.ComponentDocsCatalog.get(d.tagName, d.importFqn)
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val fxmlSnippet = esc(doc.fxmlExample)
        val javaSnippet = doc.controllerExample?.let { esc(it) }
        return buildString {
            append("<html><body style='width:460px;'>")
            append("<b>").append(d.tagName).append("</b> <span style='color:#888;'>· ").append(d.importFqn).append("</span><br>")
            append("<p>").append(esc(doc.summary)).append("</p>")
            append("<b>FXML</b><pre style='background:#1e1e1e; color:#dcdcdc; padding:6px;'>").append(fxmlSnippet).append("</pre>")
            if (javaSnippet != null) {
                append("<b>Controller (Java)</b><pre style='background:#1e1e1e; color:#dcdcdc; padding:6px;'>").append(javaSnippet).append("</pre>")
            }
            append("<p style='color:#888;'>Open the <i>Component Help</i> tool window for properties / events / Javadoc links.</p>")
            append("</body></html>")
        }
    }

    init {
        rebuild()
        // Override transferHandler with one that handles headers correctly.
        list.transferHandler = object : TransferHandler() {
            override fun getSourceActions(c: JComponent?): Int = COPY
            override fun createTransferable(c: JComponent?) =
                (list.selectedValue as? PaletteEntry.Item)?.let { ComponentTransferable(it.descriptor) }
        }
        list.addListSelectionListener {
            // Skip the selection if it landed on a header — bump to next item.
            val v = list.selectedValue
            if (v is PaletteEntry.Header) {
                val next = (list.selectedIndex + 1).takeIf { it in 0 until list.model.size }
                if (next != null) list.selectedIndex = next else list.clearSelection()
            }
        }
        add(JBScrollPane(list), BorderLayout.CENTER)
        if (project != null) {
            val bar = JBPanel<JBPanel<*>>().apply {
                add(JButton("Register custom control…").apply {
                    addActionListener {
                        if (RegisterCustomControlDialog(project).showAndGet()) rebuild()
                    }
                })
            }
            add(bar, BorderLayout.SOUTH)
        }
    }

    fun rebuild() {
        listModel.clear()
        val builtIn = PaletteEntries.all
        for (e in builtIn) listModel.addElement(e)
        if (project != null) {
            val custom = CustomControlsRegistry.getInstance(project).asDescriptors()
            if (custom.isNotEmpty()) {
                listModel.addElement(PaletteEntry.Header(ComponentDescriptor.Category.CONTROLS))
                for (d in custom) listModel.addElement(PaletteEntry.Item(d))
            }
        }
    }

    private class EntryRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            when (value) {
                is PaletteEntry.Header -> {
                    text = value.category.title
                    icon = null
                    font = font.deriveFont(Font.BOLD, font.size2D - 1f)
                    background = JBColor(0xEFEFEF.toInt(), 0x3C3F41.toInt())
                    foreground = JBColor.foreground()
                    border = JBUI.Borders.empty(4, 6, 2, 6)
                    isOpaque = true
                }
                is PaletteEntry.Item -> {
                    text = value.descriptor.displayName
                    icon = iconFor(value.descriptor)
                    font = font.deriveFont(Font.PLAIN)
                    if (!isSelected) {
                        background = list?.background ?: JBColor.background()
                        foreground = list?.foreground ?: JBColor.foreground()
                    }
                    border = JBUI.Borders.empty(2, 18, 2, 6)
                }
            }
            return c
        }

        private fun iconFor(d: ComponentDescriptor): Icon = when (d.category) {
            ComponentDescriptor.Category.CONTAINERS -> AllIcons.General.LayoutPreviewOnly
            ComponentDescriptor.Category.CONTROLS -> AllIcons.Actions.Execute
            ComponentDescriptor.Category.COLLECTIONS -> AllIcons.Nodes.DataTables
            ComponentDescriptor.Category.DISPLAY -> AllIcons.FileTypes.Image
        }
    }
}
