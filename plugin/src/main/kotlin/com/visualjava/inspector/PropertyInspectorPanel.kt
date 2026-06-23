package com.visualjava.inspector

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.visualjava.session.DesignerSessionService
import java.awt.BorderLayout
import javax.swing.SwingUtilities
import javax.swing.event.TableModelEvent
import javax.swing.table.AbstractTableModel

/**
 * Right-side property inspector. Shows the editable FXML attributes of the
 * currently selected component, and writes edits back via PSI.
 */
class PropertyInspectorPanel(private val project: Project) : JBPanel<PropertyInspectorPanel>(BorderLayout()) {

    private val header = JBLabel("(no selection)").apply {
        border = JBUI.Borders.empty(6, 8)
    }
    private val model = PropertyTableModel()
    private val table = object : JBTable(model) {
        private fun rowKind(row: Int): PropertyDescriptor.Kind? =
            (this@PropertyInspectorPanel.model.rows.getOrNull(row))?.descriptor?.kind

        override fun getCellRenderer(row: Int, column: Int): javax.swing.table.TableCellRenderer {
            if (column == 1 && rowKind(row) == PropertyDescriptor.Kind.STYLE_CLASS) return styleClassRenderer
            return super.getCellRenderer(row, column)
        }
        override fun getCellEditor(row: Int, column: Int): javax.swing.table.TableCellEditor {
            if (column == 1) {
                when (rowKind(row)) {
                    PropertyDescriptor.Kind.STYLE_CLASS -> return styleClassEditor
                    PropertyDescriptor.Kind.IMAGE -> return imageEditor
                    else -> Unit
                }
            }
            return super.getCellEditor(row, column)
        }
    }.apply {
        rowHeight = JBUI.scale(22)
        showHorizontalLines = true
        // Commit cell edits when focus moves to another component (e.g., the design canvas).
        putClientProperty("terminateEditOnFocusLost", true)
    }

    private val styleClassRenderer = StyleClassCellRenderer()
    private val styleClassEditor = StyleClassCellEditor()
    private val imageEditor = ImageCellEditor(project)

    init {
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)

        DesignerSessionService.getInstance(project).addChangeListener { refresh() }
        refresh()

        model.addTableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE && e.column == 1) {
                commit(e.firstRow)
            }
        }
    }

    private fun refresh() {
        SwingUtilities.invokeLater {
            val sel = DesignerSessionService.getInstance(project).selection
            val file = sel.file
            val fxId = sel.fxId
            if (file == null || fxId == null) {
                header.text = "(no selection)"
                model.replaceRows(emptyList())
                return@invokeLater
            }
            val xmlFile = ReadAction.compute<XmlFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(file) as? XmlFile
            } ?: run {
                header.text = "(file unavailable)"
                model.replaceRows(emptyList())
                return@invokeLater
            }
            val tag = ReadAction.compute<XmlTag?, RuntimeException> { findTagByFxId(xmlFile, fxId) }
                ?: run {
                    header.text = "$fxId — not in document"
                    model.replaceRows(emptyList())
                    return@invokeLater
                }
            val tagName = tag.localName
            val descriptors = PropertyCatalog.forTag(tagName)
            val rows = ReadAction.compute<List<Row>, RuntimeException> {
                descriptors.map { desc ->
                    val v = if (desc.name == "fx:id") tag.getAttributeValue("fx:id").orEmpty()
                    else tag.getAttributeValue(desc.name).orEmpty()
                    Row(desc, v)
                }
            }
            header.text = "$tagName · fx:id=$fxId"
            model.replaceRows(rows)
        }
    }

    private fun commit(rowIdx: Int) {
        val row = model.rows.getOrNull(rowIdx) ?: return
        val sel = DesignerSessionService.getInstance(project).selection
        val file = sel.file ?: return
        val fxId = sel.fxId ?: return

        WriteCommandAction.runWriteCommandAction(project, "Set ${row.descriptor.displayName}", null, {
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@runWriteCommandAction
            val tag = findTagByFxId(xmlFile, fxId) ?: return@runWriteCommandAction
            val attrName = row.descriptor.name
            val newValue = row.value
            if (newValue.isEmpty()) {
                tag.getAttribute(attrName)?.delete()
            } else {
                tag.setAttribute(attrName, newValue)
            }
        })
    }

    private fun findTagByFxId(file: XmlFile, fxId: String): XmlTag? {
        val root = file.rootTag ?: return null
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == fxId }
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }

    private data class Row(val descriptor: PropertyDescriptor, var value: String)

    private class PropertyTableModel : AbstractTableModel() {
        var rows: List<Row> = emptyList()

        fun replaceRows(rows: List<Row>) {
            this.rows = rows.toMutableList()
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String = if (column == 0) "Property" else "Value"
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 1
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            if (columnIndex == 0) rows[rowIndex].descriptor.displayName else rows[rowIndex].value
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex != 1) return
            (rows as MutableList)[rowIndex] = rows[rowIndex].copy(value = aValue?.toString().orEmpty())
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
}
