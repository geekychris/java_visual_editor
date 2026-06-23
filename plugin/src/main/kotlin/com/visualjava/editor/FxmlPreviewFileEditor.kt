package com.visualjava.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.Alarm
import com.visualjava.alignment.AlignActions
import com.visualjava.alignment.AlignmentSettings
import com.visualjava.alignment.DesignerToolbar
import com.visualjava.designer.ComponentDeleteHandler
import com.visualjava.designer.DesignCanvasPanel
import com.visualjava.designer.GeometryEditHandler
import com.visualjava.events.EventWiringHandler
import com.visualjava.palette.ComponentInsertHandler
import com.visualjava.pojo.PojoBindingDialog
import com.visualjava.preview.PreviewProcessService
import com.visualjava.menueditor.MenuEditorDialog
import com.visualjava.recipes.RecipeDialog
import com.visualjava.refactor.FxIdRenamer
import com.visualjava.tableeditor.TableViewColumnEditorDialog
import com.visualjava.run.RunCurrentFormAction
import com.visualjava.session.DesignerSessionService
import com.visualjava.taborder.TabOrderDialog
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Read-only preview side of the dual-tab FXML editor.
 * Renders the current document's FXML via the sidecar JavaFX process,
 * paints the resulting PNG, and routes selection / event-wiring gestures
 * back to the controller code via [EventWiringHandler].
 */
class FxmlPreviewFileEditor(
    val project: Project,
    private val file: VirtualFile,
    private val document: Document,
) : UserDataHolderBase(), FileEditor {

    private val log = thisLogger()
    private val panel = DesignCanvasPanel(project = project).also { it.fxmlFile = file }
    private val toolbar = DesignerToolbar(project)
    private val rootComponent: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(toolbar, BorderLayout.NORTH)
        add(panel, BorderLayout.CENTER)
    }
    private var loaded = false

    /** Currently-selected preview render size. 0 → use FXML root's prefWidth/prefHeight. */
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private val rerenderAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            rerenderAlarm.cancelAllRequests()
            rerenderAlarm.addRequest({ requestRender() }, 50)
        }
    }

    init {
        panel.onDoubleClick = { node ->
            wiringHandler()?.wireDefault(node)
        }
        panel.onPopupRequested = { node, mouseEvent ->
            val menu = wiringHandler()?.buildPopupMenu(node) ?: javax.swing.JPopupMenu()
            menu.addSeparator()
            val rename = javax.swing.JMenuItem("Rename fx:id…")
            rename.addActionListener {
                val newName = com.intellij.openapi.ui.Messages.showInputDialog(
                    project, "New fx:id for '${node.fxId}':", "Rename fx:id",
                    com.intellij.icons.AllIcons.Actions.RefactoringBulb, node.fxId, null,
                )?.trim().orEmpty()
                if (newName.isEmpty() || newName == node.fxId) return@addActionListener
                val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
                if (xmlFile != null) {
                    try {
                        FxIdRenamer(project).rename(xmlFile, node.fxId, newName)
                        renderNow()
                    } catch (e: IllegalArgumentException) {
                        com.intellij.openapi.ui.Messages.showErrorDialog(project, e.message ?: "Rename failed", "Cannot Rename")
                    }
                }
            }
            menu.add(rename)
            if (node.tagName == "TableView") {
                val columns = javax.swing.JMenuItem("Edit columns…")
                columns.addActionListener {
                    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
                    if (xmlFile != null) {
                        if (TableViewColumnEditorDialog(project, xmlFile, node.fxId).showAndGet()) renderNow()
                    }
                }
                menu.add(columns)
            }
            // Sample-code shortcuts (per the component-docs catalog).
            val docs = com.visualjava.help.ComponentDocsCatalog.find(node.tagName)
            if (docs != null) {
                menu.addSeparator()
                val copyFxml = javax.swing.JMenuItem("Copy ${node.tagName} FXML sample")
                copyFxml.addActionListener {
                    com.visualjava.help.SampleCodeInserter.copyToClipboard(docs.fxmlExample)
                }
                menu.add(copyFxml)
                if (!docs.controllerExample.isNullOrBlank()) {
                    val copyJava = javax.swing.JMenuItem("Copy ${node.tagName} controller sample")
                    copyJava.addActionListener {
                        com.visualjava.help.SampleCodeInserter.copyToClipboard(docs.controllerExample!!)
                    }
                    menu.add(copyJava)
                    val pasteIntoCtrl = javax.swing.JMenuItem("Paste ${node.tagName} sample into controller (/* */)")
                    pasteIntoCtrl.addActionListener {
                        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
                        if (xmlFile != null) {
                            com.visualjava.help.SampleCodeInserter.insertSampleIntoController(project, xmlFile, docs)
                        }
                    }
                    menu.add(pasteIntoCtrl)
                }
            }

            menu.addSeparator()
            val del = javax.swing.JMenuItem("Delete ${node.fxId}")
            del.addActionListener {
                val ids = panel.selectionModel.all().map { it.fxId }.ifEmpty { listOf(node.fxId) }
                val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
                if (xmlFile != null) {
                    ComponentDeleteHandler(project).delete(xmlFile, ids)
                    panel.selectionModel.clear()
                    renderNow()
                }
            }
            menu.add(del)
            menu.show(mouseEvent.component, mouseEvent.x, mouseEvent.y)
        }
        panel.selectionModel.addChangeListener {
            DesignerSessionService.getInstance(project)
                .set(file, panel.selectionModel.selected?.fxId)
        }
        panel.onComponentDrop = { descriptor, fxmlX, fxmlY, parentFxId, dropInfo ->
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
            if (xmlFile != null) {
                ComponentInsertHandler(project).insert(xmlFile, descriptor, fxmlX, fxmlY, parentFxId, dropInfo)
                renderNow()
            }
        }
        panel.onDeleteRequested = { ids ->
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
            if (xmlFile != null) {
                ComponentDeleteHandler(project).delete(xmlFile, ids)
                panel.selectionModel.clear()
                renderNow()
            }
        }
        panel.onGeometryCommit = { updates ->
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
            if (xmlFile != null) {
                GeometryEditHandler(project).commit(xmlFile, updates)
                renderNow()
            }
        }
        toolbar.installAlignButtons { kind ->
            val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@installAlignButtons
            val (fw, fh) = formBounds(xmlFile)
            val updates = AlignActions.apply(kind, panel.selectionModel.all(), fw, fh)
            if (updates.isNotEmpty()) {
                GeometryEditHandler(project).commit(xmlFile, updates)
                renderNow()
            }
        }
        toolbar.installWireUpButton  { openWireUpDialog() }
        toolbar.installPojoBindButton { openBindPojoDialog() }
        toolbar.installBulkWireButton    { wireAllUnwired() }
        toolbar.installTabOrderButton    { openTabOrder() }
        toolbar.installMenuEditorButton  { openMenuEditor() }
        toolbar.installRunButton         { runThisForm() }
        toolbar.installPreviewSizeDropdown { w, h ->
            previewWidth = w
            previewHeight = h
            renderNow()
        }
        document.addDocumentListener(documentListener, this)
    }

    // ─── Public actions (also reachable via Cmd+Shift+A "Find Action") ────────

    fun openWireUpDialog() {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return
        val byFxId = panel.currentFrameNodes().associateBy { it.fxId }
        RecipeDialog(project, xmlFile, byFxId).show()
        renderNow()
    }

    fun openBindPojoDialog() {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return
        val byFxId = panel.currentFrameNodes().associateBy { it.fxId }
        if (PojoBindingDialog(project, xmlFile, byFxId).showAndGet()) renderNow()
    }

    fun openMenuEditor() {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return
        if (MenuEditorDialog(project, xmlFile).showAndGet()) renderNow()
    }

    fun openTabOrder() {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return
        if (TabOrderDialog(project, xmlFile).showAndGet()) renderNow()
    }

    fun runThisForm() {
        RunCurrentFormAction(project).run(file)
    }

    fun wireAllUnwired() {
        val handler = wiringHandler() ?: return
        val unwired = handler.findUnwired(panel.currentFrameNodes())
        if (unwired.isEmpty()) {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project, "Every fx:id-bearing widget already has its default event wired.",
                "Nothing to wire",
            )
            return
        }
        val preview = unwired.take(8).joinToString("\n") { (n, ev) -> "  • ${n.fxId}  (${ev.property})" }
        val extra = if (unwired.size > 8) "\n  …and ${unwired.size - 8} more" else ""
        val choice = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "Wire ${unwired.size} default handlers?\n\n$preview$extra",
            "Bulk Wire",
            com.intellij.openapi.ui.Messages.getQuestionIcon(),
        )
        if (choice == com.intellij.openapi.ui.Messages.YES) {
            handler.wireBulk(unwired)
            renderNow()
        }
    }

    /** Form size derived from the root tag's prefWidth/prefHeight (FXML coords). */
    private fun formBounds(xmlFile: XmlFile): Pair<Int, Int> {
        val root = xmlFile.rootTag
        val w = root?.getAttributeValue("prefWidth")?.toDoubleOrNull()?.toInt() ?: 800
        val h = root?.getAttributeValue("prefHeight")?.toDoubleOrNull()?.toInt() ?: 600
        return w to h
    }

    private fun wiringHandler(): EventWiringHandler? {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        return EventWiringHandler(project, xmlFile)
    }

    override fun getComponent(): JComponent = rootComponent
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Design"
    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit

    override fun selectNotify() {
        if (!loaded) {
            loaded = true
            requestRender()
        }
        DesignerSessionService.getInstance(project)
            .set(file, panel.selectionModel.selected?.fxId)
    }

    /**
     * Cancel any debounced re-render and trigger one immediately. Used after our
     * own designer-driven mutations (drop / delete / move / align) so consecutive
     * gestures see fresh node bounds.
     */
    private fun renderNow() {
        rerenderAlarm.cancelAllRequests()
        requestRender()
    }

    fun requestRender() {
        val fxml = document.text
        if (fxml.isBlank()) {
            SwingUtilities.invokeLater { panel.setStatus("(empty file)") }
            return
        }
        SwingUtilities.invokeLater { panel.setStatus("Rendering…") }
        val (rw, rh) = resolveRenderSize()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = PreviewProcessService.getInstance(project)
                val stylesheets = com.intellij.openapi.application.ReadAction.compute<List<String>, RuntimeException> {
                    com.visualjava.preview.ProjectStylesheets.discover(project)
                }
                val frame = service.client().render(fxml, rw, rh, stylesheets)
                SwingUtilities.invokeLater { panel.setFrame(frame) }
            } catch (e: Exception) {
                log.warn("Preview render failed", e)
                SwingUtilities.invokeLater { panel.setStatus("Render failed: ${e.message}") }
            }
        }
    }

    /** If the user chose "Form bounds" (0×0), read prefWidth/Height from root; default 800×600. */
    private fun resolveRenderSize(): Pair<Int, Int> {
        if (previewWidth > 0 && previewHeight > 0) return previewWidth to previewHeight
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return 800 to 600
        val (fw, fh) = formBounds(xmlFile)
        // Add a small margin so the form isn't pressed against the canvas edge.
        return (fw + 40).coerceAtLeast(400) to (fh + 40).coerceAtLeast(300)
    }
}
