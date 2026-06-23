package com.visualjava.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Registers the dual-tab editor for every `.fxml` file in the project.
 * Composes a [TextEditorWithPreview]: standard XML editor on one side,
 * [FxmlPreviewFileEditor] on the other.
 */
class FxmlDesignerEditorProvider : FileEditorProvider, DumbAware {

    override fun getEditorTypeId(): String = "visual-java.fxml-designer"

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension.equals("fxml", ignoreCase = true)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val document = textEditor.editor.document
        val preview = FxmlPreviewFileEditor(project, file, document)

        return TextEditorWithPreview(
            textEditor,
            preview,
            "Visual Java FXML Designer",
            TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW,
        )
    }

    /**
     * Hide ALL other registered editors for .fxml. The platform's bundled JavaFX
     * plugin ships a Scene Builder editor (org.jetbrains.plugins.javaFX) whose
     * lazy initialisation runs file-index lookups on the EDT — when the user
     * toggles layout in our dual-tab editor, that triggers a re-init that
     * locks the IDE. We don't want a competing editor anyway: our dual-tab
     * already provides the source view via the text editor side.
     */
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}
