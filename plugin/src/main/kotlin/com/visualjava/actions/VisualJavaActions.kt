package com.visualjava.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.visualjava.editor.FxmlPreviewFileEditor

/**
 * IntelliJ-action wrappers for the designer toolbar's commands.
 *
 * Registering these means **Cmd+Shift+A → "Visual Java — Wire-Up Recipe"** (or
 * any of the others) opens the same dialog the toolbar button does. That's
 * what the cliclick capture script relies on — we drive everything by typing
 * action names, no pixel coordinates.
 */

/** Find the active FXML preview editor, or null if no FXML is selected. */
internal fun findActiveFxmlEditor(project: Project): FxmlPreviewFileEditor? {
    val sel = FileEditorManager.getInstance(project).selectedEditor ?: return null
    if (sel is TextEditorWithPreview) {
        val preview = sel.previewEditor
        return preview as? FxmlPreviewFileEditor
    }
    return sel as? FxmlPreviewFileEditor
}

/**
 * Base for "do something with the active FXML editor" actions. Hidden when no
 * FXML editor is selected (so they don't clutter Find Action for unrelated
 * file types).
 */
abstract class FxmlEditorAction(name: String) : AnAction(name), DumbAware {
    final override fun update(e: AnActionEvent) {
        val p = e.project
        e.presentation.isEnabledAndVisible = p != null && findActiveFxmlEditor(p) != null
    }
    final override fun actionPerformed(e: AnActionEvent) {
        val p = e.project ?: return
        val editor = findActiveFxmlEditor(p) ?: return
        invoke(editor)
    }
    protected abstract fun invoke(editor: FxmlPreviewFileEditor)
}

class WireUpRecipeAction : FxmlEditorAction("Visual Java — Wire-Up Recipe…") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.openWireUpDialog()
}

class BindPojoAction : FxmlEditorAction("Visual Java — Bind POJO…") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.openBindPojoDialog()
}

class MenuEditorAction : FxmlEditorAction("Visual Java — Menu Editor…") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.openMenuEditor()
}

class TabOrderAction : FxmlEditorAction("Visual Java — Tab Order…") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.openTabOrder()
}

class RunFormAction : FxmlEditorAction("Visual Java — Run This Form") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.runThisForm()
}

class WireAllUnwiredAction : FxmlEditorAction("Visual Java — Wire All Unwired") {
    override fun invoke(editor: FxmlPreviewFileEditor) = editor.wireAllUnwired()
}
