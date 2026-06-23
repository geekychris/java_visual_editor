package com.visualjava.tableeditor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Visual TableView column editor.
 *
 * Reads the current `<columns>` block of the selected TableView, presents an
 * editable table of (Header, fx:id, Property, Pref Width, Sortable, Resizable),
 * then writes the column tags back via PSI.
 *
 * For v1 this only emits PropertyValueFactory-based cell value factories
 * (`<cellValueFactory><PropertyValueFactory property="…"/></cellValueFactory>`),
 * which is what 90% of TableViews need.
 */
class TableViewColumnEditorDialog(
    private val project: Project,
    private val fxmlFile: XmlFile,
    private val tableFxId: String,
) : DialogWrapper(project, true) {

    private val columnsModel = ColumnTableModel()
    private val table = JBTable(columnsModel).apply {
        rowHeight = JBUI.scale(24)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    init {
        title = "Columns of $tableFxId"
        loadExistingColumns()
        init()
    }

    private fun loadExistingColumns() {
        val tag = findTableTag() ?: return
        val cols = tag.findFirstSubTag("columns") ?: return
        val rows = cols.subTags
            .filter { it.localName == "TableColumn" }
            .map { col ->
                val pvf = col.findFirstSubTag("cellValueFactory")?.findFirstSubTag("PropertyValueFactory")
                Row(
                    header = col.getAttributeValue("text").orEmpty(),
                    fxId = col.getAttributeValue("fx:id").orEmpty(),
                    property = pvf?.getAttributeValue("property").orEmpty(),
                    prefWidth = col.getAttributeValue("prefWidth")?.toIntOrNull() ?: 100,
                    sortable = col.getAttributeValue("sortable")?.toBooleanStrictOrNull() ?: true,
                    resizable = col.getAttributeValue("resizable")?.toBooleanStrictOrNull() ?: true,
                )
            }
        columnsModel.replaceRows(rows)
    }

    override fun createCenterPanel(): JComponent {
        val buttons = JBPanel<JBPanel<*>>(GridLayout(0, 1, 4, 4)).apply {
            border = JBUI.Borders.empty(8)
            add(JButton("+ Add").apply { addActionListener {
                columnsModel.add(Row("New Column", "col${columnsModel.rowCount + 1}", "newProperty", 120, true, true))
                table.editCellAt(columnsModel.rowCount - 1, 0)
            } })
            add(JButton("Remove").apply { addActionListener {
                val r = table.selectedRow; if (r >= 0) columnsModel.remove(r)
            } })
            add(JButton("▲ Up").apply { addActionListener {
                val r = table.selectedRow; if (r > 0) { columnsModel.swap(r, r - 1); table.setRowSelectionInterval(r - 1, r - 1) }
            } })
            add(JButton("▼ Down").apply { addActionListener {
                val r = table.selectedRow; if (r in 0 until columnsModel.rowCount - 1) { columnsModel.swap(r, r + 1); table.setRowSelectionInterval(r + 1, r + 1) }
            } })
            preferredSize = Dimension(110, 200)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(720, 360)
            add(JBLabel("Each row is one column on $tableFxId. The Property column is the bean property name used by PropertyValueFactory.")
                .apply { border = JBUI.Borders.empty(6, 8) }, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    override fun doOKAction() {
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        WriteCommandAction.runWriteCommandAction(project, "Edit TableView columns", null, {
            ensureImport("javafx.scene.control.TableColumn")
            ensureImport("javafx.scene.control.cell.PropertyValueFactory")
            val tag = findTableTag() ?: return@runWriteCommandAction
            val existing = tag.findFirstSubTag("columns")
            existing?.delete()
            val factory = XmlElementFactory.getInstance(project)
            val newCols = factory.createTagFromText(buildColumnsXml())
            tag.addSubTag(newCols, false)
        })
        super.doOKAction()
    }

    private fun buildColumnsXml(): String = buildString {
        append("<columns>")
        for (r in columnsModel.rows) {
            append("<TableColumn")
            if (r.fxId.isNotBlank()) append(" fx:id=\"${esc(r.fxId)}\"")
            append(" text=\"${esc(r.header)}\"")
            append(" prefWidth=\"${r.prefWidth}\"")
            append(" sortable=\"${r.sortable}\"")
            append(" resizable=\"${r.resizable}\"")
            append(">")
            if (r.property.isNotBlank()) {
                append("<cellValueFactory>")
                append("<PropertyValueFactory property=\"${esc(r.property)}\"/>")
                append("</cellValueFactory>")
            }
            append("</TableColumn>")
        }
        append("</columns>")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun ensureImport(fqn: String) {
        val needle = "<?import $fqn?>"
        val doc = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(fxmlFile) ?: return
        val text = doc.text
        if (text.contains(needle)) return
        val regex = Regex("<\\?import [^?]+\\?>")
        val matches = regex.findAll(text).toList()
        val insertAt = if (matches.isNotEmpty()) matches.last().range.last + 1
        else (text.indexOf("?>", text.indexOf("<?xml")) + 2).coerceAtLeast(0)
        doc.insertString(insertAt, "\n$needle")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(doc)
    }

    private fun findTableTag(): XmlTag? {
        val root = fxmlFile.rootTag ?: return null
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == tableFxId }
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }

    private data class Row(
        var header: String,
        var fxId: String,
        var property: String,
        var prefWidth: Int,
        var sortable: Boolean,
        var resizable: Boolean,
    )

    private inner class ColumnTableModel : AbstractTableModel() {
        var rows = mutableListOf<Row>()
            private set

        fun replaceRows(rs: List<Row>) {
            rows = rs.toMutableList()
            fireTableDataChanged()
        }
        fun add(r: Row) { rows.add(r); fireTableRowsInserted(rows.size - 1, rows.size - 1) }
        fun remove(i: Int) { rows.removeAt(i); fireTableRowsDeleted(i, i) }
        fun swap(a: Int, b: Int) { val t = rows[a]; rows[a] = rows[b]; rows[b] = t; fireTableDataChanged() }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 6
        override fun getColumnName(c: Int): String = when (c) {
            0 -> "Header"; 1 -> "fx:id"; 2 -> "Property"; 3 -> "Pref Width"; 4 -> "Sortable"; else -> "Resizable"
        }
        override fun isCellEditable(r: Int, c: Int): Boolean = true
        override fun getColumnClass(c: Int): Class<*> = when (c) {
            3 -> Integer::class.java
            4, 5 -> java.lang.Boolean::class.java
            else -> String::class.java
        }
        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> rows[r].header
            1 -> rows[r].fxId
            2 -> rows[r].property
            3 -> rows[r].prefWidth
            4 -> rows[r].sortable
            else -> rows[r].resizable
        }
        override fun setValueAt(v: Any?, r: Int, c: Int) {
            when (c) {
                0 -> rows[r].header = v?.toString().orEmpty()
                1 -> rows[r].fxId = v?.toString().orEmpty()
                2 -> rows[r].property = v?.toString().orEmpty()
                3 -> rows[r].prefWidth = (v as? Int) ?: v?.toString()?.toIntOrNull() ?: rows[r].prefWidth
                4 -> rows[r].sortable = (v as? Boolean) ?: rows[r].sortable
                5 -> rows[r].resizable = (v as? Boolean) ?: rows[r].resizable
            }
            fireTableCellUpdated(r, c)
        }
    }
}
