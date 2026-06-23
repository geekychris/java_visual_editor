package com.visualjava.a11y

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.SwingUtilities
import javax.swing.event.TableModelEvent
import javax.swing.table.AbstractTableModel

/**
 * Accessibility inspector. For the current FXML, lists every focusable / labeled
 * widget with its accessible-* attributes and a "warnings" column flagging
 * common gaps (missing accessibleText on icon-only buttons, etc.).
 *
 * Edits in the table commit straight to the FXML via PSI — same machinery as
 * the property inspector.
 */
class AccessibilityPanel(private val project: Project) :
    JBPanel<AccessibilityPanel>(BorderLayout()) {

    private val header = JBLabel("(no FXML open)").apply { border = JBUI.Borders.empty(6, 8) }
    private val model = A11yTableModel()
    private val table = JBTable(model).apply {
        rowHeight = JBUI.scale(22)
        showHorizontalLines = true
        putClientProperty("terminateEditOnFocusLost", true)
    }
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    @Volatile private var activeFile: VirtualFile? = null
    private val docListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            refreshAlarm.cancelAllRequests()
            refreshAlarm.addRequest({ refresh() }, 200)
        }
    }

    init {
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    onActiveFileChanged(event.newFile)
                }
            },
        )
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let { onActiveFileChanged(it) }

        model.addTableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE && e.column in 1..3) commitRow(e.firstRow, e.column)
        }
    }

    private fun onActiveFileChanged(file: VirtualFile?) {
        activeFile?.let { old ->
            FileDocumentManager.getInstance().getDocument(old)?.removeDocumentListener(docListener)
        }
        activeFile = file?.takeIf { it.extension.equals("fxml", true) }
        activeFile?.let { f ->
            FileDocumentManager.getInstance().getDocument(f)?.addDocumentListener(docListener)
        }
        refresh()
    }

    private fun refresh() {
        SwingUtilities.invokeLater {
            val file = activeFile
            if (file == null) {
                header.text = "(no FXML open)"
                model.replace(emptyList())
                return@invokeLater
            }
            val xmlFile = ReadAction.compute<XmlFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(file) as? XmlFile
            } ?: return@invokeLater
            val rows = ReadAction.compute<List<Row>, RuntimeException> {
                val out = mutableListOf<Row>()
                val root = xmlFile.rootTag ?: return@compute emptyList<Row>()
                walk(root, out)
                out
            }
            header.text = "${rows.size} focusable widget(s) — ${file.name}"
            model.replace(rows)
        }
    }

    private fun commitRow(rowIdx: Int, col: Int) {
        val row = model.rows.getOrNull(rowIdx) ?: return
        val file = activeFile ?: return
        val attr = when (col) {
            1 -> "accessibleText"
            2 -> "accessibleRole"
            3 -> "accessibleHelp"
            else -> return
        }
        val value = when (col) {
            1 -> row.accessibleText
            2 -> row.accessibleRole
            3 -> row.accessibleHelp
            else -> ""
        }
        WriteCommandAction.runWriteCommandAction(project, "Set $attr on ${row.fxId}", null, {
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@runWriteCommandAction
            val tag = findByFxId(xmlFile, row.fxId) ?: return@runWriteCommandAction
            if (value.isBlank()) tag.getAttribute(attr)?.delete() else tag.setAttribute(attr, value)
        })
    }

    private fun walk(tag: XmlTag, out: MutableList<Row>) {
        val fxId = tag.getAttributeValue("fx:id")
        if (fxId != null && tag.localName in FOCUSABLE) {
            val text = tag.getAttributeValue("text").orEmpty()
            val accText = tag.getAttributeValue("accessibleText").orEmpty()
            val accRole = tag.getAttributeValue("accessibleRole").orEmpty()
            val accHelp = tag.getAttributeValue("accessibleHelp").orEmpty()
            val warning = lint(tag.localName, text, accText)
            out.add(Row(tag.localName, fxId, text, accText, accRole, accHelp, warning))
        }
        for (child in tag.subTags) walk(child, out)
    }

    private fun lint(tagName: String, text: String, accText: String): String = when {
        tagName == "Button" && text.isBlank() && accText.isBlank() ->
            "Icon-only button without accessibleText — screen readers will read nothing"
        tagName in setOf("TextField", "PasswordField") && accText.isBlank() && text.isBlank() ->
            "Field has no visible label or accessibleText — assistive tech can't name it"
        tagName == "ImageView" && accText.isBlank() ->
            "ImageView without accessibleText — set one or mark decorative"
        else -> ""
    }

    private fun findByFxId(file: XmlFile, fxId: String): XmlTag? {
        val root = file.rootTag ?: return null
        fun walk(t: XmlTag): XmlTag? {
            if (t.getAttributeValue("fx:id") == fxId) return t
            for (c in t.subTags) walk(c)?.let { return it }
            return null
        }
        return walk(root)
    }

    data class Row(
        val tagName: String,
        val fxId: String,
        val text: String,
        var accessibleText: String,
        var accessibleRole: String,
        var accessibleHelp: String,
        val warning: String,
    )

    private class A11yTableModel : AbstractTableModel() {
        var rows: List<Row> = emptyList()
        fun replace(rows: List<Row>) { this.rows = rows.toMutableList(); fireTableDataChanged() }
        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 5
        override fun getColumnName(column: Int): String = when (column) {
            0 -> "Widget"; 1 -> "accessibleText"; 2 -> "accessibleRole"
            3 -> "accessibleHelp"; else -> "Warnings"
        }
        override fun isCellEditable(row: Int, col: Int): Boolean = col in 1..3
        override fun getValueAt(row: Int, col: Int): Any = when (col) {
            0 -> "${rows[row].tagName} · ${rows[row].fxId}"
            1 -> rows[row].accessibleText
            2 -> rows[row].accessibleRole
            3 -> rows[row].accessibleHelp
            else -> rows[row].warning
        }
        override fun setValueAt(value: Any?, row: Int, col: Int) {
            val v = value?.toString().orEmpty()
            val r = rows[row]
            (rows as MutableList)[row] = when (col) {
                1 -> r.copy(accessibleText = v)
                2 -> r.copy(accessibleRole = v)
                3 -> r.copy(accessibleHelp = v)
                else -> r
            }
            fireTableCellUpdated(row, col)
        }
    }

    companion object {
        private val FOCUSABLE = setOf(
            "Button", "ToggleButton", "MenuButton", "Hyperlink",
            "TextField", "PasswordField", "TextArea",
            "CheckBox", "RadioButton",
            "ComboBox", "ChoiceBox", "DatePicker", "ColorPicker", "Spinner", "Slider",
            "ListView", "TableView", "TreeView", "TreeTableView", "TabPane",
            "ImageView",
        )
    }
}

