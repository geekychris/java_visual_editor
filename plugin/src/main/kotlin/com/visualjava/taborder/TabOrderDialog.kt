package com.visualjava.taborder

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler

/**
 * Visual tab-order editor (the VB6 "Tab Index" dialog).
 *
 * Lists every focusable widget on the form in current tab order (depth-first
 * sibling order in FXML), with Move Up / Move Down to reorder. Pressing OK
 * rewrites the FXML so that the corresponding tags appear in the chosen order
 * within their parent's `<children>` collection — JavaFX traverses focus in
 * scene-graph order, so reordering the FXML is the canonical fix.
 *
 * For v1 the dialog only reorders **direct children of the same parent**.
 * Reparenting across containers is out of scope.
 */
class TabOrderDialog(
    private val project: Project,
    private val fxmlFile: XmlFile,
) : DialogWrapper(project, true) {

    private val listModel = DefaultListModel<Entry>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                src: JList<*>?, value: Any?, index: Int, sel: Boolean, focused: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(src, value, index, sel, focused)
                if (value is Entry) {
                    val parentLabel = value.parentPath.substringBefore('#')
                    text = "${index + 1}.  ${value.tagName} · ${value.fxId}  —  in $parentLabel"
                }
                return c
            }
        }
        dragEnabled = true
        dropMode = DropMode.INSERT
        transferHandler = EntryListTransferHandler()
    }

    /**
     * Drag-drop reorder. Source: the selected entry's index (carried as a String).
     * Drop: insert at the target index, removing the original. Cross-parent
     * drops carry the dropped entry's [Entry.parentPath] over — the OK action
     * relocates the FXML tag into the new parent's `<children>`.
     */
    private inner class EntryListTransferHandler : TransferHandler() {
        private val flavor = DataFlavor(String::class.java, "application/x-vj-tab-order-index")

        override fun getSourceActions(c: JComponent?): Int = MOVE
        override fun createTransferable(c: JComponent?): Transferable? {
            val idx = list.selectedIndex
            if (idx < 0) return null
            return object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(flavor)
                override fun isDataFlavorSupported(f: DataFlavor): Boolean = f == flavor
                override fun getTransferData(f: DataFlavor): Any = idx.toString()
            }
        }

        override fun canImport(support: TransferSupport): Boolean =
            support.isDataFlavorSupported(flavor) && support.isDrop

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val from = (support.transferable.getTransferData(flavor) as String).toInt()
            val loc = support.dropLocation as JList.DropLocation
            var to = loc.index
            if (from < 0 || from >= listModel.size()) return false
            val moving = listModel.get(from)

            // Decide the parent the dropped entry now belongs to: the parent of
            // the surrounding row (the one above the gap), or the row below if
            // dropped at the top. Falls back to the moving entry's current parent.
            val anchorIdx = (to - 1).coerceAtLeast(0).coerceAtMost(listModel.size() - 1)
            val newParent = listModel.get(anchorIdx).parentPath.takeIf { listModel.size() > 0 } ?: moving.parentPath

            listModel.remove(from)
            if (to > from) to-- // account for the removed source
            val relocated = moving.copy(parentPath = newParent)
            listModel.add(to.coerceIn(0, listModel.size()), relocated)
            list.selectedIndex = to
            return true
        }
    }

    init {
        title = "Tab Order"
        populate()
        init()
    }

    private fun populate() {
        listModel.clear()
        val root = fxmlFile.rootTag ?: return
        // Group focusable elements by their parent so reordering is intra-parent.
        for ((parent, entries) in collectFocusableByParent(root)) {
            for (e in entries) listModel.addElement(e.copy(parentPath = parent))
        }
    }

    private fun collectFocusableByParent(root: XmlTag): Map<String, List<Entry>> {
        val out = linkedMapOf<String, MutableList<Entry>>()
        fun walk(tag: XmlTag, parentKey: String) {
            for (child in tag.subTags) {
                if (child.localName == "children") {
                    val key = parentKey
                    for (grand in child.subTags) {
                        val fxId = grand.getAttributeValue("fx:id")
                        if (fxId != null && isFocusable(grand.localName)) {
                            out.getOrPut(key) { mutableListOf() }
                                .add(Entry(grand.localName, fxId, key))
                        }
                        walk(grand, "${grand.localName}#$fxId")
                    }
                } else {
                    walk(child, parentKey)
                }
            }
        }
        walk(root, "${root.localName}#root")
        return out
    }

    private fun isFocusable(tagName: String): Boolean = tagName in FOCUSABLE_TAGS

    override fun createCenterPanel(): JComponent {
        val buttons = JBPanel<JBPanel<*>>(GridLayout(0, 1, 4, 4)).apply {
            border = JBUI.Borders.empty(8)
            add(JButton("▲ Move Up").apply { addActionListener { move(-1) } })
            add(JButton("▼ Move Down").apply { addActionListener { move(1) } })
            preferredSize = Dimension(140, 200)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(620, 400)
            add(JBLabel("Drag to reorder, or use the buttons. Drop on another parent to reparent.")
                .apply { border = JBUI.Borders.empty(6, 8) }, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    private fun move(direction: Int) {
        val i = list.selectedIndex
        if (i < 0) return
        val j = i + direction
        if (j !in 0 until listModel.size()) return
        val v = listModel.get(i)
        // Cross-parent move via buttons: adopt the neighbour's parent.
        val neighbourParent = listModel.get(j).parentPath
        listModel.remove(i)
        listModel.add(j, v.copy(parentPath = neighbourParent))
        list.selectedIndex = j
    }

    override fun doOKAction() {
        WriteCommandAction.runWriteCommandAction(project, "Tab order", null, {
            // Snapshot the current FXML state of every focusable tag (we lift
            // them out of their old parents and re-place them under the new
            // target parents in the order the user chose). Snapshotting before
            // any deletions avoids the "re-parented mid-iteration" hazard from
            // the pitfalls memory.
            val entries = (0 until listModel.size()).map { listModel.get(it) }
            val byFxId = mutableMapOf<String, XmlTag>()
            val root = fxmlFile.rootTag ?: return@runWriteCommandAction
            for (t in walk(root)) {
                val id = t.getAttributeValue("fx:id") ?: continue
                if (entries.any { it.fxId == id }) byFxId[id] = t
            }

            // Detach everything first, then re-attach in chosen order.
            for (t in byFxId.values) t.delete()
            val grouped = entries.groupBy { it.parentPath }
            for ((parentPath, group) in grouped) {
                val parent = findParentByPath(parentPath) ?: continue
                val children = parent.findFirstSubTag("children")
                    ?: parent.addSubTag(
                        com.intellij.psi.XmlElementFactory.getInstance(project).createTagFromText("<children/>"),
                        false,
                    )
                for (e in group) {
                    val tag = byFxId[e.fxId] ?: continue
                    children.addSubTag(tag, false)
                }
            }
        })
        super.doOKAction()
    }

    private fun findParentByPath(parentPath: String): XmlTag? {
        // parentPath format: "<tagName>#<fxId>" or "<tagName>#root"
        val fxId = parentPath.substringAfter('#')
        val root = fxmlFile.rootTag ?: return null
        if (fxId == "root") return root
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == fxId }
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }

    data class Entry(val tagName: String, val fxId: String, val parentPath: String)

    companion object {
        private val FOCUSABLE_TAGS = setOf(
            "Button", "ToggleButton", "MenuButton", "Hyperlink",
            "TextField", "PasswordField", "TextArea",
            "CheckBox", "RadioButton",
            "ComboBox", "ChoiceBox", "DatePicker", "ColorPicker", "Spinner", "Slider",
            "ListView", "TableView", "TreeView", "TreeTableView", "TabPane",
        )
    }
}
