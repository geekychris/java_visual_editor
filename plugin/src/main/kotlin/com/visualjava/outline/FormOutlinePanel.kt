package com.visualjava.outline

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.visualjava.session.DesignerSessionService
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import com.intellij.ide.util.PropertiesComponent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Hierarchy view of the active FXML file. Click a node → selects it in the
 * canvas (via [DesignerSessionService]); the property inspector reacts too.
 */
class FormOutlinePanel(private val project: Project) : JBPanel<FormOutlinePanel>(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("(no FXML open)")
    private val model = DefaultTreeModel(rootNode)
    private val tree = JTree(model).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        isRootVisible = true
        showsRootHandles = true
    }
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    @Volatile private var activeFile: VirtualFile? = null
    private val docListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            refreshAlarm.cancelAllRequests()
            refreshAlarm.addRequest({ refresh() }, 200)
        }
    }

    private val showAnonymousKey = "visualjava.outline.showAnonymous"
    private var showAnonymous: Boolean
        get() = PropertiesComponent.getInstance(project).getBoolean(showAnonymousKey, true)
        set(value) { PropertiesComponent.getInstance(project).setValue(showAnonymousKey, value, true) }

    init {
        val toolbar = JPanel(BorderLayout())
        val anonToggle = JCheckBox("Show wrapper nodes (no fx:id)", showAnonymous)
        anonToggle.toolTipText = "Show anonymous wrapper tags in the tree (otherwise only fx:id-bearing nodes appear)"
        anonToggle.addActionListener {
            showAnonymous = anonToggle.isSelected
            refresh()
        }
        toolbar.add(anonToggle, BorderLayout.WEST)
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.addTreeSelectionListener(TreeSelectionListener {
            val n = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@TreeSelectionListener
            val payload = n.userObject as? OutlineEntry ?: return@TreeSelectionListener
            val file = activeFile ?: return@TreeSelectionListener
            DesignerSessionService.getInstance(project).set(file, payload.fxId)
        })

        // Track the currently active editor file.
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    onActiveFileChanged(event.newFile)
                }
            },
        )
        // Initial state
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let { onActiveFileChanged(it) }
    }

    private fun onActiveFileChanged(file: VirtualFile?) {
        // Detach old listener
        activeFile?.let { old ->
            val oldDoc = FileDocumentManager.getInstance().getDocument(old)
            oldDoc?.removeDocumentListener(docListener)
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
                rootNode.userObject = "(no FXML open)"
                rootNode.removeAllChildren()
                model.reload()
                return@invokeLater
            }
            val xmlFile = ReadAction.compute<XmlFile?, RuntimeException> {
                PsiManager.getInstance(project).findFile(file) as? XmlFile
            }
            val rootTag = xmlFile?.rootTag
            if (rootTag == null) {
                rootNode.userObject = file.name
                rootNode.removeAllChildren()
                model.reload()
                return@invokeLater
            }
            val newRoot = ReadAction.compute<DefaultMutableTreeNode, RuntimeException> {
                buildNode(rootTag)
            }
            rootNode.userObject = newRoot.userObject
            // Snapshot children first — `rootNode.add(child)` reparents and removes
            // the node from newRoot, which would shift indices mid-iteration.
            val children = (0 until newRoot.childCount).map { newRoot.getChildAt(it) as DefaultMutableTreeNode }
            rootNode.removeAllChildren()
            children.forEach { rootNode.add(it) }
            model.reload()
            // Auto-expand a couple levels for usability.
            for (i in 0 until tree.rowCount) tree.expandRow(i)
        }
    }

    private fun buildNode(tag: XmlTag): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(OutlineEntry(tag.localName, tag.getAttributeValue("fx:id")))
        for (child in tag.subTags) {
            // Skip the FXML wrapper "children" element; treat its contents as direct kids.
            if (child.localName == "children") {
                for (grand in child.subTags) {
                    if (!showAnonymous && grand.getAttributeValue("fx:id") == null) continue
                    node.add(buildNode(grand))
                }
            } else {
                if (!showAnonymous && child.getAttributeValue("fx:id") == null) continue
                node.add(buildNode(child))
            }
        }
        return node
    }

    private data class OutlineEntry(val tagName: String, val fxId: String?) {
        override fun toString(): String = if (fxId != null) "$tagName · $fxId" else tagName
    }
}
