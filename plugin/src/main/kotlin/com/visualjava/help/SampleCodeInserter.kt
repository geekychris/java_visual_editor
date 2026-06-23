package com.visualjava.help

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.visualjava.codegen.FxmlControllerResolver
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Drop the controller sample code from a [ComponentDoc] into the FXML's
 * controller class, wrapped in a `/* … */` block so the user can copy/uncomment
 * the parts they want without breaking the file.
 *
 * The block is inserted at the top of the class body, with a header line
 * naming the widget. If the controller class doesn't exist yet,
 * [FxmlControllerResolver.findOrCreateController] creates one — same path
 * the wire-up recipes already take.
 */
object SampleCodeInserter {

    fun copyToClipboard(text: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }

    /**
     * Insert the [doc]'s controller example into the controller for [fxmlFile].
     * Returns true if something was inserted, false if there's no controller
     * sample for this widget (the caller may show a balloon).
     */
    fun insertSampleIntoController(project: Project, fxmlFile: XmlFile, doc: ComponentDoc): Boolean {
        val sample = doc.controllerExample
        if (sample.isNullOrBlank()) return false

        val comment = buildString {
            append("/* --- Sample code: ").append(doc.tagName).append(" ---\n")
            append("   Uncomment and adapt; remove the comment markers when done.\n")
            append("\n")
            sample.lineSequence().forEach { append(it).append("\n") }
            append("*/\n")
        }

        WriteCommandAction.runWriteCommandAction(project, "Paste sample code: ${doc.tagName}", null, {
            val cls = try {
                FxmlControllerResolver(project).findOrCreateController(fxmlFile)
            } catch (e: FxmlControllerResolver.NoSuitableTargetException) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showWarningDialog(project, e.message ?: "Couldn't find controller", "Paste sample code")
                }
                return@runWriteCommandAction
            }

            val psiFile = cls.containingFile ?: return@runWriteCommandAction
            val doc2: Document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runWriteCommandAction

            // Insert just after the class's opening brace so the comment sits at
            // the top of the body. lBrace().textRange.endOffset is the precise spot.
            val lbrace = cls.lBrace ?: return@runWriteCommandAction
            val insertOffset = lbrace.textRange.endOffset
            doc2.insertString(insertOffset, "\n\n    " + comment.replace("\n", "\n    ").trimEnd())
            PsiDocumentManager.getInstance(project).commitDocument(doc2)

            // Open the file at the inserted location so the user can see it.
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(project)
                    .openTextEditor(OpenFileDescriptor(project, psiFile.virtualFile, insertOffset + 1), true)
            }
        })
        return true
    }
}
