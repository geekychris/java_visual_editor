package com.visualjava.projectview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Project-scoped tool window: every `.fxml` form in the project, grouped under
 * its source folder, with the associated controller class as a child node.
 *
 * Double-click a form → open it in the Designer.
 * Double-click a controller → open the .java file.
 *
 * Refreshes when files are added/deleted/renamed via a VFS bulk listener.
 */
class VisualJavaProjectView(private val project: Project) : JBPanel<VisualJavaProjectView>(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Visual Java forms")
    private val model = DefaultTreeModel(rootNode)
    private val tree = JTree(model).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        isRootVisible = true
        showsRootHandles = true
    }
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

    init {
        add(JBScrollPane(tree), BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val path: TreePath = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val payload = node.userObject) {
                    is FormEntry -> FileEditorManager.getInstance(project).openFile(payload.file, true)
                    is ControllerEntry -> FileEditorManager.getInstance(project).openFile(payload.file, true)
                }
            }
        })

        // Listen for VFS changes anywhere in the project.
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val relevant = events.any { e ->
                        val name = e.path.substringAfterLast('/')
                        name.endsWith(".fxml", true) || name.endsWith(".java", true)
                    }
                    if (relevant) scheduleRefresh()
                }
            },
        )

        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refresh() }, 200)
    }

    /**
     * Off-EDT scan via ReadAction, results re-posted to EDT for tree rebuild.
     * `FilenameIndex.getAllFilesByExt` and PSI walks are slow operations the
     * platform forbids on EDT.
     */
    private fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val pairs = ReadAction.compute<List<Pair<VirtualFile, VirtualFile?>>, RuntimeException> {
                val forms = findFxmlFiles(project)
                forms.map { it to resolveController(it) }
            }
            SwingUtilities.invokeLater {
                rootNode.removeAllChildren()
                rootNode.userObject = "Visual Java forms (${pairs.size})"
                for ((form, ctrl) in pairs.sortedBy { it.first.path }) {
                    val node = DefaultMutableTreeNode(FormEntry(form))
                    if (ctrl != null) node.add(DefaultMutableTreeNode(ControllerEntry(ctrl)))
                    rootNode.add(node)
                }
                model.reload()
                for (i in 0 until tree.rowCount) tree.expandRow(i)
            }
        }
    }

    private fun findFxmlFiles(project: Project): List<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val index = ProjectFileIndex.getInstance(project)
        return FilenameIndex.getAllFilesByExt(project, "fxml", scope)
            .filter { it.isValid && index.isInContent(it) }
            .distinct()
    }

    private fun resolveController(fxml: VirtualFile): VirtualFile? {
        val xmlFile = PsiManager.getInstance(project).findFile(fxml) as? XmlFile ?: return null
        val fqn = xmlFile.rootTag?.getAttributeValue("fx:controller") ?: return null
        val psiClass = com.intellij.psi.JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project)) ?: return null
        return psiClass.containingFile?.virtualFile
    }

    private data class FormEntry(val file: VirtualFile) {
        override fun toString(): String {
            val rel = file.path.substringAfter("src/main/resources/", file.name)
            return "${file.nameWithoutExtension}  —  ${if (rel == file.name) file.name else rel}"
        }
    }

    private data class ControllerEntry(val file: VirtualFile) {
        override fun toString(): String = "↳ ${file.nameWithoutExtension}.java"
    }
}
