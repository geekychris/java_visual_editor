package com.visualjava.palette

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/**
 * Register a user-authored control as a palette entry. Asks for the FQN, gives
 * the user a chance to override the displayed name and a few default attrs,
 * then writes the entry to the per-project registry. Re-opening the palette
 * picks up the new entry.
 */
class RegisterCustomControlDialog(private val project: Project) : DialogWrapper(project, true) {

    private val fqn = JBTextField()
    private val displayName = JBTextField()
    private val defaultAttrs = JBTextField("prefWidth=120 prefHeight=24")

    init {
        title = "Register Custom Control"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Class FQN:", fqn)
            .addLabeledComponent("Display name (optional):", displayName)
            .addLabeledComponent("Default attrs (space-separated key=value):", defaultAttrs)
            .addComponentToRightColumn(JBLabel("Stored in .idea/visualjava-custom-controls.json; shareable via VCS."))
            .panel
            .apply { border = JBUI.Borders.empty(8); preferredSize = java.awt.Dimension(520, 200) }
    }

    override fun doValidate(): ValidationInfo? {
        if (fqn.text.isBlank() || !fqn.text.contains('.')) {
            return ValidationInfo("Fully-qualified class name required (e.g. com.example.MyButton)", fqn)
        }
        return null
    }

    override fun doOKAction() {
        val fq = fqn.text.trim()
        val tag = fq.substringAfterLast('.')
        val dispText = displayName.text.trim().ifBlank { tag }
        val attrMap = parseAttrs(defaultAttrs.text)
        val entry = CustomControlEntry()
        entry.tagName = tag
        entry.displayName = dispText
        entry.importFqn = fq
        entry.defaultAttrs = attrMap
        CustomControlsRegistry.getInstance(project).add(entry)
        super.doOKAction()
    }

    private fun parseAttrs(s: String): Map<String, String> {
        if (s.isBlank()) return emptyMap()
        return s.trim().split(Regex("\\s+"))
            .mapNotNull {
                val eq = it.indexOf('=')
                if (eq < 0) null else it.substring(0, eq) to it.substring(eq + 1)
            }.toMap()
    }
}
