package com.visualjava.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * VB6-style "Resources" pane. Lists everything under each module's resource
 * source roots, categorised by purpose (Images / CSS / FXML / Bundles / Other).
 * Double-click opens; the toolbar has an Import button that copies an external
 * file into the chosen subfolder of resources/.
 *
 * Scope: read-only browsing + import. Renaming / deleting goes through
 * IntelliJ's project view (right-click → Refactor → Rename, etc.) — we don't
 * duplicate those affordances here.
 */
class ResourceManagerPanel(private val project: Project) :
    JBPanel<ResourceManagerPanel>(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Resources")
    private val model = DefaultTreeModel(rootNode)
    private val tree = JTree(model).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        isRootVisible = true
        showsRootHandles = true
    }

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val n = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                val vf = (n.userObject as? ResourceEntry)?.file ?: return
                FileEditorManager.getInstance(project).openFile(vf, true)
            }
        })
        refresh()
    }

    private fun buildToolbar(): JBPanel<*> {
        val bar = JBPanel<JBPanel<*>>(BorderLayout()).apply { border = JBUI.Borders.empty(4) }
        val refresh = JButton("Refresh").apply { addActionListener { refresh() } }
        val import = JButton("Import…").apply { addActionListener { importFile() } }
        val buttons = JBPanel<JBPanel<*>>().apply { add(refresh); add(import) }
        bar.add(JBLabel("Project resources"), BorderLayout.WEST)
        bar.add(buttons, BorderLayout.EAST)
        return bar
    }

    private fun importFile() {
        val target = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.let { it.userObject as? ResourceEntry }
            ?.file?.takeIf { it.isDirectory }
            ?: resourceRoots().firstOrNull()
            ?: return
        val desc = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Import resource into ${target.path}")
        val picked = FileChooser.chooseFile(desc, project, null) ?: return
        ApplicationManager.getApplication().runWriteAction {
            VfsUtil.copyFile(this, picked, target)
        }
        refresh()
    }

    private fun resourceRoots(): List<VirtualFile> = ReadAction.compute<List<VirtualFile>, RuntimeException> {
        ModuleManager.getInstance(project).modules
            .flatMap { ModuleRootManager.getInstance(it).getSourceRoots(true).toList() }
            .filter { it.path.contains("/resources/") || it.path.endsWith("/resources") }
            .distinct()
    }

    fun refresh() {
        rootNode.removeAllChildren()
        for (root in resourceRoots()) {
            val moduleNode = DefaultMutableTreeNode(ResourceEntry(root.name, root))
            populate(moduleNode, root)
            rootNode.add(moduleNode)
        }
        model.reload()
        for (i in 0 until tree.rowCount.coerceAtMost(20)) tree.expandRow(i)
    }

    private fun populate(parent: DefaultMutableTreeNode, dir: VirtualFile) {
        for (child in dir.children.sortedWith(compareBy({ !it.isDirectory }, { it.name }))) {
            if (child.name.startsWith(".") || child.name == "build") continue
            val node = DefaultMutableTreeNode(ResourceEntry(child.name, child))
            if (child.isDirectory) populate(node, child)
            parent.add(node)
        }
    }

    private data class ResourceEntry(val label: String, val file: VirtualFile) {
        override fun toString(): String = when {
            file.isDirectory -> "📁 $label"
            file.extension?.lowercase() in setOf("png", "jpg", "jpeg", "gif", "bmp", "svg") -> "🖼️ $label"
            file.extension?.equals("css", ignoreCase = true) == true -> "🎨 $label"
            file.extension?.equals("fxml", ignoreCase = true) == true -> "📄 $label"
            file.extension?.equals("properties", ignoreCase = true) == true -> "🌐 $label"
            else -> label
        }
    }
}
