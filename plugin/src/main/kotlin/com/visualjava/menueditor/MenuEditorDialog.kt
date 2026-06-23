package com.visualjava.menueditor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * The VB6 Menu Editor — tree view of MenuBar → Menu → MenuItem with text,
 * fx:id, accelerator, and a handler-name field per row. Pressing OK writes a
 * `<MenuBar fx:id="…">` block at the top of the FXML's root <children>, plus
 * @FXML fields and handler method stubs in the controller.
 *
 * Limitations for v1: edits build a new MenuBar from scratch — re-running
 * with an existing MenuBar in the form is not yet a round-trip (the old one
 * stays; you'd get two). Use it on forms without a MenuBar yet.
 */
class MenuEditorDialog(
    private val project: Project,
    private val fxmlFile: XmlFile,
) : DialogWrapper(project, true) {

    private val rootNode = DefaultMutableTreeNode("MenuBar")
    private val model = DefaultTreeModel(rootNode)
    private val tree = JTree(model).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        isRootVisible = true
        showsRootHandles = true
    }

    private val textField = JBTextField()
    private val fxIdField = JBTextField()
    private val acceleratorField = JBTextField()
    private val handlerField = JBTextField()
    private val separatorBefore = JCheckBox("Insert separator before this item")
    private val checkedBox = JCheckBox("Checkable (CheckMenuItem)")

    init {
        title = "Menu Editor"
        if (!loadExistingMenuBar()) seedDefaultStructure()
        model.reload()
        for (i in 0 until tree.rowCount) tree.expandRow(i)

        tree.addTreeSelectionListener { onSelectionChanged() }
        textField.addActionListener { applyFieldsToSelection() }
        fxIdField.addActionListener { applyFieldsToSelection() }
        acceleratorField.addActionListener { applyFieldsToSelection() }
        handlerField.addActionListener { applyFieldsToSelection() }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val left = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JBLabel("Menu structure").apply { border = JBUI.Borders.empty(4, 6) }, BorderLayout.NORTH)
            add(JBScrollPane(tree), BorderLayout.CENTER)
            add(buildTreeButtons(), BorderLayout.SOUTH)
            preferredSize = Dimension(280, 380)
        }
        val right = FormBuilder.createFormBuilder()
            .addLabeledComponent("Text:", textField)
            .addLabeledComponent("fx:id:", fxIdField)
            .addLabeledComponent("Accelerator:", acceleratorField)
            .addLabeledComponent("Handler name (no #):", handlerField)
            .addComponent(separatorBefore)
            .addComponent(checkedBox)
            .addComponentToRightColumn(JBLabel("Click items in the tree to edit. Press Enter in a field to apply.")
                .apply { foreground = JBColor.GRAY })
            .panel
            .apply { preferredSize = Dimension(360, 380); border = JBUI.Borders.empty(8) }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.CENTER)
        }
    }

    private fun buildTreeButtons(): JComponent = JBPanel<JBPanel<*>>(GridLayout(1, 0, 4, 4)).apply {
        border = JBUI.Borders.empty(4)
        add(JButton("+ Menu").apply { addActionListener { addMenu() } })
        add(JButton("+ Item").apply { addActionListener { addItem() } })
        add(JButton("Remove").apply { addActionListener { removeSelected() } })
        add(JButton("▲").apply { addActionListener { moveSibling(-1) } })
        add(JButton("▼").apply { addActionListener { moveSibling(1) } })
    }

    private fun onSelectionChanged() {
        val sel = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? MenuNode ?: return
        textField.text = sel.text
        fxIdField.text = sel.fxId
        acceleratorField.text = sel.accelerator
        handlerField.text = sel.handler
        separatorBefore.isSelected = sel.separatorBefore
        checkedBox.isSelected = sel.checkable
    }

    private fun applyFieldsToSelection() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val cur = node.userObject as? MenuNode ?: return
        node.userObject = cur.copy(
            text = textField.text.orEmpty(),
            fxId = fxIdField.text.orEmpty(),
            accelerator = acceleratorField.text.orEmpty(),
            handler = handlerField.text.orEmpty(),
            separatorBefore = separatorBefore.isSelected,
            checkable = checkedBox.isSelected,
        )
        model.nodeChanged(node)
    }

    private fun addMenu() {
        rootNode.add(DefaultMutableTreeNode(
            MenuNode("New Menu", "newMenu", "", "", false, false, NodeKind.MENU)
        ))
        model.reload()
    }

    private fun addItem() {
        val sel = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: rootNode
        val target = if ((sel.userObject as? MenuNode)?.kind == NodeKind.MENU) sel else sel.parent as? DefaultMutableTreeNode ?: rootNode
        target.add(DefaultMutableTreeNode(
            MenuNode("New Item", "newItem", "", "onItem", false, false, NodeKind.ITEM)
        ))
        model.reload()
    }

    private fun removeSelected() {
        val sel = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        if (sel === rootNode) return
        val parent = sel.parent as? DefaultMutableTreeNode ?: return
        parent.remove(sel)
        model.reload()
    }

    private fun moveSibling(direction: Int) {
        val sel = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val parent = sel.parent as? DefaultMutableTreeNode ?: return
        val i = parent.getIndex(sel)
        val j = i + direction
        if (j !in 0 until parent.childCount) return
        parent.remove(sel)
        parent.insert(sel, j)
        model.reload()
    }

    /** Build the seed structure used when no MenuBar is present in the FXML. */
    private fun seedDefaultStructure() {
        rootNode.userObject = MenuNode("MenuBar", "menuBar", "", "", false, false, NodeKind.MENU_BAR)
        val fileMenu = DefaultMutableTreeNode(MenuNode("File", "fileMenu", "", "", false, false, NodeKind.MENU))
        val editMenu = DefaultMutableTreeNode(MenuNode("Edit", "editMenu", "", "", false, false, NodeKind.MENU))
        val helpMenu = DefaultMutableTreeNode(MenuNode("Help", "helpMenu", "", "", false, false, NodeKind.MENU))
        fileMenu.add(DefaultMutableTreeNode(MenuNode("New",   "newMenuItem",  "Shortcut+N", "onNew",  false, false, NodeKind.ITEM)))
        fileMenu.add(DefaultMutableTreeNode(MenuNode("Open…", "openMenuItem", "Shortcut+O", "onOpen", false, false, NodeKind.ITEM)))
        fileMenu.add(DefaultMutableTreeNode(MenuNode("Save",  "saveMenuItem", "Shortcut+S", "onSave", false, false, NodeKind.ITEM)))
        fileMenu.add(DefaultMutableTreeNode(MenuNode("Exit",  "exitMenuItem", "Shortcut+Q", "onExit", true,  false, NodeKind.ITEM)))
        editMenu.add(DefaultMutableTreeNode(MenuNode("Undo",  "undoMenuItem", "Shortcut+Z",       "onUndo", false, false, NodeKind.ITEM)))
        editMenu.add(DefaultMutableTreeNode(MenuNode("Redo",  "redoMenuItem", "Shortcut+Shift+Z", "onRedo", false, false, NodeKind.ITEM)))
        helpMenu.add(DefaultMutableTreeNode(MenuNode("About…","aboutMenuItem", "", "onAbout", false, false, NodeKind.ITEM)))
        rootNode.add(fileMenu); rootNode.add(editMenu); rootNode.add(helpMenu)
    }

    /**
     * If a MenuBar exists in the FXML, parse it into the tree so opening the
     * editor on an already-edited form round-trips. Returns true if loaded.
     */
    private fun loadExistingMenuBar(): Boolean {
        val root = fxmlFile.rootTag ?: return false
        val bar = findFirstByLocalName(root, "MenuBar") ?: return false
        val barFxId = bar.getAttributeValue("fx:id") ?: "menuBar"
        rootNode.userObject = MenuNode("MenuBar", barFxId, "", "", false, false, NodeKind.MENU_BAR)
        val menusContainer = bar.findFirstSubTag("menus") ?: bar
        for (menu in menusContainer.subTags.filter { it.localName == "Menu" }) {
            val mFxId = menu.getAttributeValue("fx:id") ?: ""
            val mText = menu.getAttributeValue("text") ?: ""
            val menuNode = DefaultMutableTreeNode(MenuNode(mText, mFxId, "", "", false, false, NodeKind.MENU))
            val items = menu.findFirstSubTag("items") ?: menu
            var pendingSeparator = false
            for (item in items.subTags) {
                when (item.localName) {
                    "SeparatorMenuItem" -> { pendingSeparator = true }
                    "MenuItem", "CheckMenuItem" -> {
                        val iFxId = item.getAttributeValue("fx:id") ?: ""
                        val iText = item.getAttributeValue("text") ?: ""
                        val iAccel = item.getAttributeValue("accelerator") ?: ""
                        val onAction = item.getAttributeValue("onAction") ?: ""
                        val handler = if (onAction.startsWith("#")) onAction.substring(1) else onAction
                        val checkable = item.localName == "CheckMenuItem"
                        menuNode.add(DefaultMutableTreeNode(
                            MenuNode(iText, iFxId, iAccel, handler, pendingSeparator, checkable, NodeKind.ITEM)
                        ))
                        pendingSeparator = false
                    }
                }
            }
            rootNode.add(menuNode)
        }
        return true
    }

    private fun findFirstByLocalName(start: XmlTag, name: String): XmlTag? {
        if (start.localName == name) return start
        for (child in start.subTags) {
            val found = findFirstByLocalName(child, name)
            if (found != null) return found
        }
        return null
    }

    override fun doOKAction() {
        applyFieldsToSelection()  // make sure any in-flight edits land
        WriteCommandAction.runWriteCommandAction(project, "Apply Menu Editor", null, {
            // Delete the existing MenuBar (if any) so we don't end up with two.
            fxmlFile.rootTag?.let { root ->
                findFirstByLocalName(root, "MenuBar")?.delete()
            }
            writeMenuBarToFxml()
        })
        super.doOKAction()
    }

    private fun writeMenuBarToFxml() {
        val root = fxmlFile.rootTag ?: return
        val children = root.findFirstSubTag("children") ?: return

        // Ensure required <?import?> declarations exist on the FXML.
        ensureImport("javafx.scene.control.MenuBar")
        ensureImport("javafx.scene.control.Menu")
        ensureImport("javafx.scene.control.MenuItem")
        if (anyCheckable()) ensureImport("javafx.scene.control.CheckMenuItem")
        if (anySeparator()) ensureImport("javafx.scene.control.SeparatorMenuItem")

        val bar = rootNode.userObject as MenuNode
        val xml = buildString {
            append("<MenuBar fx:id=\"${bar.fxId}\" layoutX=\"0\" layoutY=\"0\" prefWidth=\"600\">")
            append("<menus>")
            for (i in 0 until rootNode.childCount) {
                val menuNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
                val m = menuNode.userObject as MenuNode
                append("<Menu fx:id=\"${m.fxId}\" text=\"${escape(m.text)}\">")
                append("<items>")
                for (j in 0 until menuNode.childCount) {
                    val itemNode = menuNode.getChildAt(j) as DefaultMutableTreeNode
                    val it = itemNode.userObject as MenuNode
                    if (it.separatorBefore) append("<SeparatorMenuItem/>")
                    val tag = if (it.checkable) "CheckMenuItem" else "MenuItem"
                    append("<$tag fx:id=\"${it.fxId}\" text=\"${escape(it.text)}\"")
                    if (it.accelerator.isNotBlank()) append(" accelerator=\"${escape(it.accelerator)}\"")
                    if (it.handler.isNotBlank()) append(" onAction=\"#${it.handler}\"")
                    append("/>")
                }
                append("</items>")
                append("</Menu>")
            }
            append("</menus>")
            append("</MenuBar>")
        }

        val factory = XmlElementFactory.getInstance(project)
        val newTag = factory.createTagFromText(xml)
        // Insert at the BEGINNING of <children> so the MenuBar sits at the top.
        val existing = children.subTags
        if (existing.isEmpty()) {
            children.addSubTag(newTag, false)
        } else {
            children.addSubTag(newTag, true)
        }
    }

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

    private fun anyCheckable(): Boolean = anyItem { it.checkable }
    private fun anySeparator(): Boolean = anyItem { it.separatorBefore }
    private fun anyItem(predicate: (MenuNode) -> Boolean): Boolean {
        for (i in 0 until rootNode.childCount) {
            val menu = rootNode.getChildAt(i) as DefaultMutableTreeNode
            for (j in 0 until menu.childCount) {
                val n = (menu.getChildAt(j) as DefaultMutableTreeNode).userObject as MenuNode
                if (predicate(n)) return true
            }
        }
        return false
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")

    enum class NodeKind { MENU_BAR, MENU, ITEM }
    data class MenuNode(
        val text: String,
        val fxId: String,
        val accelerator: String,
        val handler: String,
        val separatorBefore: Boolean,
        val checkable: Boolean,
        val kind: NodeKind,
    ) {
        override fun toString(): String = "$text   [${fxId}]"
    }
}
