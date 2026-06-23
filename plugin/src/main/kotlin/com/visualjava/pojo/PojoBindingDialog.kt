package com.visualjava.pojo

import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.codegen.FxmlControllerResolver
import com.visualjava.preview.PreviewClient
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

/**
 * Pick a Java class → list its bean properties → assign each to a widget on
 * the form (by fx:id). Click OK and we emit `bind(pojo)` + `save(pojo)` in
 * the controller, plus `@FXML` fields for every assigned widget.
 */
class PojoBindingDialog(
    private val project: Project,
    private val fxmlFile: XmlFile,
    private val nodes: Map<String, PreviewClient.NodeBounds>,
) : DialogWrapper(project, true) {

    private val classField = JBTextField().apply { preferredSize = Dimension(360, preferredSize.height) }
    private val browseBtn = JButton("Browse…")
    private val statusLabel = JBLabel(" ").apply { foreground = com.intellij.ui.JBColor.GRAY }
    private val introspector = PojoIntrospector(project)
    private val model = MappingTableModel()
    private val table = JBTable(model).apply {
        rowHeight = JBUI.scale(24)
        autoCreateRowSorter = true
        // Column 2 (widget): editable combo of compatible fxIds (plus "(skip)")
        columnModel.getColumn(2).cellEditor = createWidgetEditor()
        columnModel.getColumn(2).cellRenderer = TableCellRenderer { tbl, value, sel, focus, row, col ->
            JLabel(value?.toString() ?: "")
        }
    }
    private var resolvedClass: PsiClass? = null

    init {
        title = "POJO Binding Wizard"
        browseBtn.addActionListener { browse() }
        classField.addActionListener { tryResolve() }
        init()
    }

    private fun browse() {
        val chooser = TreeClassChooserFactory.getInstance(project)
            .createProjectScopeChooser("Pick a class to bind")
        chooser.showDialog()
        val sel = chooser.selected
        if (sel != null) {
            classField.text = sel.qualifiedName
            tryResolve(sel)
        }
    }

    private fun tryResolve(pre: PsiClass? = null) {
        val cls = pre ?: introspector.findClass(classField.text.trim())
        if (cls == null) {
            statusLabel.text = "Class not found on the project classpath."
            statusLabel.foreground = com.intellij.ui.JBColor.RED
            resolvedClass = null
            model.replaceRows(emptyList())
            return
        }
        resolvedClass = cls
        val props = introspector.findProperties(cls)
        if (props.isEmpty()) {
            statusLabel.text = "No bean properties (getter+setter pairs) found on ${cls.name}."
            statusLabel.foreground = com.intellij.ui.JBColor.GRAY
        } else {
            statusLabel.text = "${cls.name}: ${props.size} bindable propert${if (props.size == 1) "y" else "ies"}"
            statusLabel.foreground = com.intellij.ui.JBColor.GRAY
        }
        model.replaceRows(props.map { Row(it, suggestFxId(it)) })
    }

    /** First fxId of a compatible widget whose name matches the property name (substring). */
    private fun suggestFxId(p: PojoIntrospector.BeanProperty): String {
        val want = PojoBinder.defaultWidgetFor(p.kind)
        val matches = nodes.values.filter { it.tagName == want }
        val byName = matches.firstOrNull { it.fxId.equals(p.name, ignoreCase = true) || it.fxId.lowercase().contains(p.name.lowercase()) }
        return byName?.fxId ?: matches.firstOrNull()?.fxId ?: SKIP
    }

    private fun createWidgetEditor(): DefaultCellEditor {
        val combo = JComboBox<String>()
        // Items are reset before each edit based on the current row's property kind.
        return object : DefaultCellEditor(combo) {
            override fun getTableCellEditorComponent(
                tbl: javax.swing.JTable, value: Any?, isSelected: Boolean, row: Int, col: Int,
            ): java.awt.Component {
                combo.removeAllItems()
                combo.addItem(SKIP)
                val prop = model.rows[row].property
                val preferred = PojoBinder.defaultWidgetFor(prop.kind)
                for (n in nodes.values.sortedBy { it.fxId }) {
                    combo.addItem(n.fxId)
                    if (n.tagName == preferred && combo.selectedItem != n.fxId && value != n.fxId) {
                        // leave default selection alone here; we set it below
                    }
                }
                combo.selectedItem = value?.toString() ?: SKIP
                return combo
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val top = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)
            add(JBLabel("Class FQN:").apply { border = JBUI.Borders.emptyRight(6) }, BorderLayout.WEST)
            add(classField, BorderLayout.CENTER)
            add(browseBtn, BorderLayout.EAST)
        }
        val center = JBScrollPane(table)
        val bottom = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 8)
            add(statusLabel, BorderLayout.WEST)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(720, 420)
            add(top, BorderLayout.NORTH)
            add(center, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    override fun doOKAction() {
        val cls = resolvedClass
        if (cls == null) {
            Messages.showErrorDialog(project, "Pick a valid class first.", "No Class")
            return
        }
        if (table.isEditing) table.cellEditor?.stopCellEditing()
        val mappings = model.rows.mapNotNull { row ->
            val fxId = row.fxId.takeIf { it.isNotBlank() && it != SKIP } ?: return@mapNotNull null
            val tag = nodes[fxId]?.tagName ?: return@mapNotNull null
            PojoBinder.Mapping(row.property, fxId, tag)
        }
        if (mappings.isEmpty()) {
            Messages.showInfoMessage(project, "Nothing selected to bind.", "Nothing to do")
            return
        }
        WriteCommandAction.runWriteCommandAction(project, "Bind ${cls.name}", null, {
            val controller = FxmlControllerResolver(project).findOrCreateController(fxmlFile)
            val codeGen = ControllerCodeGenerator(project)
            PojoBinder(codeGen).emit(controller, cls.qualifiedName ?: cls.name ?: "Object", mappings)
        })
        super.doOKAction()
    }

    private data class Row(val property: PojoIntrospector.BeanProperty, var fxId: String)

    private inner class MappingTableModel : AbstractTableModel() {
        var rows: MutableList<Row> = mutableListOf()
            private set

        fun replaceRows(rs: List<Row>) {
            rows = rs.toMutableList()
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(c: Int): String = when (c) {
            0 -> "Property"
            1 -> "Type"
            else -> "Widget (fx:id)"
        }
        override fun isCellEditable(r: Int, c: Int): Boolean = c == 2
        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> rows[r].property.name
            1 -> rows[r].property.typeShort
            else -> rows[r].fxId
        }
        override fun setValueAt(value: Any?, r: Int, c: Int) {
            if (c == 2) rows[r].fxId = value?.toString().orEmpty()
            fireTableCellUpdated(r, c)
        }
    }

    companion object {
        private const val SKIP = "(skip)"
    }
}
