package com.visualjava.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextArea
import javax.swing.ListSelectionModel

class NewFxmlFileAction : AnAction(
    "FXML Form",
    "Create a new Visual Java FXML form from a template",
    AllIcons.FileTypes.Xml,
), DumbAware {

    override fun update(e: AnActionEvent) {
        val hasContext = e.project != null && e.getData(LangDataKeys.IDE_VIEW)?.directories?.isNotEmpty() == true
        e.presentation.isEnabledAndVisible = hasContext
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val view: IdeView = e.getData(LangDataKeys.IDE_VIEW) ?: return
        val directory: PsiDirectory = view.orChooseDirectory ?: return

        val dialog = NewFormDialog(project)
        if (!dialog.showAndGet()) return
        val template = dialog.selectedTemplate
        val rawName = dialog.nameField.text?.trim().orEmpty()
        if (rawName.isEmpty() || !rawName.matches(Regex("[A-Za-z0-9_\\-.]+"))) {
            Messages.showErrorDialog(project, "Form name must be a valid file-name fragment.", "Invalid Name")
            return
        }
        val fileName = if (rawName.endsWith(".fxml", ignoreCase = true)) rawName else "$rawName.fxml"
        if (directory.findFile(fileName) != null) {
            Messages.showErrorDialog(project, "$fileName already exists in ${directory.virtualFile.path}", "Cannot Create Form")
            return
        }

        val virtualFile = WriteAction.compute<com.intellij.openapi.vfs.VirtualFile, RuntimeException> {
            val vf = directory.virtualFile.createChildData(this, fileName)
            vf.setBinaryContent(template.fxml.toByteArray(Charsets.UTF_8))
            vf
        }
        PsiManager.getInstance(project).findFile(virtualFile)?.let { view.selectElement(it) }
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
}

/** Dialog that picks a template + asks for the form name. */
private class NewFormDialog(project: Project) : DialogWrapper(project, true) {

    val nameField = JBTextField("MyForm").apply { preferredSize = Dimension(260, preferredSize.height) }

    private val templateList = JBList(*FormTemplate.values()).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, selected, focused)
                if (value is FormTemplate) text = value.displayName
                return c
            }
        }
        selectedIndex = 0
    }

    private val descriptionArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = null
        border = JBUI.Borders.empty(8)
    }

    val selectedTemplate: FormTemplate get() = templateList.selectedValue ?: FormTemplate.BLANK

    init {
        title = "New FXML Form"
        templateList.addListSelectionListener {
            descriptionArea.text = (templateList.selectedValue ?: FormTemplate.BLANK).description
        }
        descriptionArea.text = FormTemplate.BLANK.description
        init()
    }

    override fun createCenterPanel(): JComponent {
        val left = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JBLabel("Template").apply { border = JBUI.Borders.empty(4, 6) }, BorderLayout.NORTH)
            add(JBScrollPane(templateList), BorderLayout.CENTER)
            preferredSize = Dimension(220, 320)
        }
        val right = FormBuilder.createFormBuilder()
            .addComponent(descriptionArea)
            .addSeparator()
            .addLabeledComponent("Form name (no .fxml needed):", nameField)
            .panel
            .apply { preferredSize = Dimension(360, 320) }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField
}
