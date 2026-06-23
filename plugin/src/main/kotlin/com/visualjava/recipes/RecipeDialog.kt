package com.visualjava.recipes

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.codegen.FxmlControllerResolver
import com.visualjava.preview.PreviewClient
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextArea
import javax.swing.ListSelectionModel

/**
 * Dialog to pick a [Recipe] and assign one of the form's components to each
 * of its roles. Pressing OK runs the recipe inside a WriteCommandAction.
 */
class RecipeDialog(
    private val project: Project,
    private val fxmlFile: XmlFile,
    private val nodesByFxId: Map<String, PreviewClient.NodeBounds>,
) : DialogWrapper(project, true) {

    private val recipeList = JBList(*Recipes.all.toTypedArray()).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean,
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, selected, focused)
                if (value is Recipe) text = value.name
                return c
            }
        }
        selectedIndex = 0
    }

    private val description = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = null
        border = JBUI.Borders.empty()
    }

    private val rolesPanel = JBPanel<JBPanel<*>>(GridBagLayout())
    private val roleCombos = mutableMapOf<String, JComboBox<String>>()

    init {
        title = "Wire-Up Recipe"
        recipeList.addListSelectionListener { rebuildRoles() }
        rebuildRoles()
        init()
    }

    private fun rebuildRoles() {
        rolesPanel.removeAll()
        roleCombos.clear()
        val recipe = recipeList.selectedValue ?: return
        description.text = recipe.description

        val c = GridBagConstraints()
        c.gridx = 0; c.gridy = 0
        c.anchor = GridBagConstraints.WEST
        c.insets = JBUI.insets(4, 8, 4, 8)
        for (role in recipe.roles) {
            val candidates = nodesByFxId.values.filter { role.accepts(it.tagName) }
            val combo = JComboBox<String>().apply {
                if (role.optional) addItem("(none)")
                candidates.forEach { addItem("${it.fxId} — ${it.tagName}") }
                preferredSize = Dimension(260, preferredSize.height)
            }
            roleCombos[role.key] = combo
            c.gridx = 0
            rolesPanel.add(JBLabel("${role.displayName}:"), c)
            c.gridx = 1
            rolesPanel.add(combo, c)
            if (role.description.isNotEmpty()) {
                c.gridy++
                c.gridx = 1
                rolesPanel.add(JBLabel("  ${role.description}").apply {
                    foreground = com.intellij.ui.JBColor.GRAY
                }, c)
            }
            c.gridy++
        }
        rolesPanel.revalidate()
        rolesPanel.repaint()
    }

    override fun createCenterPanel(): JComponent {
        val left = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JBLabel("Recipe").apply { border = JBUI.Borders.empty(4, 6) }, BorderLayout.NORTH)
            add(JBScrollPane(recipeList), BorderLayout.CENTER)
            preferredSize = Dimension(220, 360)
        }
        val right = FormBuilder.createFormBuilder()
            .addComponent(description)
            .addSeparator()
            .addLabeledComponent("", rolesPanel, 0, false)
            .panel
            .apply { preferredSize = Dimension(420, 360) }

        val root = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.CENTER)
        }
        return root
    }

    override fun doOKAction() {
        val recipe = recipeList.selectedValue ?: return super.doOKAction()
        val assignments = mutableMapOf<String, String>()
        for (role in recipe.roles) {
            val combo = roleCombos[role.key] ?: continue
            val selected = combo.selectedItem as? String ?: continue
            if (selected == "(none)") continue
            val fxId = selected.substringBefore(" — ")
            if (fxId.isNotEmpty()) assignments[role.key] = fxId
        }

        WriteCommandAction.runWriteCommandAction(project, "Wire-up: ${recipe.name}", null, {
            val resolver = FxmlControllerResolver(project)
            val controller = resolver.findOrCreateController(fxmlFile)
            val codeGen = ControllerCodeGenerator(project)
            val freshXml = PsiManager.getInstance(project).findFile(fxmlFile.virtualFile) as? XmlFile ?: fxmlFile
            val ctx = RecipeContext(project, freshXml, controller, codeGen, nodesByFxId)
            recipe.generate(ctx, assignments)
        })
        super.doOKAction()
    }
}
