package com.visualjava.pojo

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.codegen.FxmlControllerResolver

/**
 * Pick a Java class → generate a complete FXML form with one labelled widget
 * per bean property, plus a Save button. Auto-applies POJO binding so the
 * generated controller already has bind/save methods.
 */
class FormFromPojoAction : AnAction(
    "FXML Form from POJO…",
    "Generate a form with one widget per property of the chosen class",
    AllIcons.Actions.IntentionBulb,
), DumbAware {

    override fun update(e: AnActionEvent) {
        val ok = e.project != null && e.getData(LangDataKeys.IDE_VIEW)?.directories?.isNotEmpty() == true
        e.presentation.isEnabledAndVisible = ok
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val view: IdeView = e.getData(LangDataKeys.IDE_VIEW) ?: return
        val directory: PsiDirectory = view.orChooseDirectory ?: return

        val chooser = TreeClassChooserFactory.getInstance(project).createProjectScopeChooser("Pick a class")
        chooser.showDialog()
        val cls = chooser.selected ?: return
        val pojoFqn = cls.qualifiedName ?: cls.name ?: return

        val introspector = PojoIntrospector(project)
        val props = introspector.findProperties(cls)
        if (props.isEmpty()) {
            Messages.showInfoMessage(project, "${cls.name} has no bean properties (need getter + setter pairs).", "No properties")
            return
        }

        val rawName = Messages.showInputDialog(
            project, "Form file name (no .fxml):", "Form from POJO",
            AllIcons.FileTypes.Xml, "${cls.name}Form", null,
        )?.trim().orEmpty()
        if (rawName.isEmpty()) return
        val fileName = if (rawName.endsWith(".fxml", true)) rawName else "$rawName.fxml"
        if (directory.findFile(fileName) != null) {
            Messages.showErrorDialog(project, "$fileName already exists.", "Cannot Create")
            return
        }

        val fxml = buildFxml(props)
        val virtualFile = WriteAction.compute<com.intellij.openapi.vfs.VirtualFile, RuntimeException> {
            val vf = directory.virtualFile.createChildData(this, fileName)
            vf.setBinaryContent(fxml.toByteArray(Charsets.UTF_8))
            vf
        }
        PsiManager.getInstance(project).findFile(virtualFile)?.let { view.selectElement(it) }
        FileEditorManager.getInstance(project).openFile(virtualFile, true)

        // Auto-apply POJO binding so bind()/save() land in the controller too.
        val xmlFile = PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile
        if (xmlFile != null) {
            val mappings = props.map {
                PojoBinder.Mapping(it, fxIdFor(it), PojoBinder.defaultWidgetFor(it.kind))
            }
            WriteCommandAction.runWriteCommandAction(project, "Bind ${cls.name}", null, {
                val controller = FxmlControllerResolver(project).findOrCreateController(xmlFile)
                val codeGen = ControllerCodeGenerator(project)
                PojoBinder(codeGen).emit(controller, pojoFqn, mappings)
            })
        }
    }

    private fun fxIdFor(p: PojoIntrospector.BeanProperty): String =
        "${p.name}${PojoBinder.defaultWidgetFor(p.kind)}"
            .replaceFirstChar { it.lowercase() }

    /** Build a vertical labelled form, one row per property. */
    private fun buildFxml(props: List<PojoIntrospector.BeanProperty>): String {
        val rowHeight = 32
        val topPadding = 24
        val labelX = 24
        val widgetX = 180
        val widgetWidth = 280
        val formHeight = topPadding + props.size * rowHeight + 60

        val imports = sortedSetOf<String>().apply {
            add("javafx.scene.control.Button")
            add("javafx.scene.control.Label")
            add("javafx.scene.layout.AnchorPane")
            for (p in props) {
                val w = PojoBinder.defaultWidgetFor(p.kind)
                add("javafx.scene.control.$w")
                if (w == "ColorPicker") add("javafx.scene.paint.Color")
            }
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            for (i in imports) append("<?import $i?>\n")
            append("\n")
            append("<AnchorPane xmlns=\"http://javafx.com/javafx\" xmlns:fx=\"http://javafx.com/fxml\"\n")
            append("            prefWidth=\"520\" prefHeight=\"$formHeight\">\n")
            append("    <children>\n")
            for ((i, p) in props.withIndex()) {
                val y = topPadding + i * rowHeight
                val fxId = fxIdFor(p)
                val widget = PojoBinder.defaultWidgetFor(p.kind)
                val labelText = humanise(p.name)
                append("        <Label text=\"$labelText\" layoutX=\"$labelX\" layoutY=\"${y + 4}\"/>\n")
                append("        <$widget fx:id=\"$fxId\" layoutX=\"$widgetX\" layoutY=\"$y\" prefWidth=\"$widgetWidth\"")
                when (widget) {
                    "CheckBox" -> append(" text=\"\"")
                    "Slider" -> when (p.kind) {
                        PojoIntrospector.Kind.FLOAT, PojoIntrospector.Kind.DOUBLE ->
                            append(" min=\"0\" max=\"1\" value=\"0\"")
                        else -> append(" min=\"0\" max=\"100\" value=\"0\"")
                    }
                    else -> Unit
                }
                append("/>\n")
            }
            val saveY = topPadding + props.size * rowHeight + 16
            append("        <Button fx:id=\"saveBtn\" text=\"Save\" layoutX=\"${widgetX + widgetWidth - 100}\" layoutY=\"$saveY\"")
            append(" prefWidth=\"100\" defaultButton=\"true\"/>\n")
            append("    </children>\n")
            append("</AnchorPane>\n")
        }
    }

    /** "firstName" → "First name:" */
    private fun humanise(s: String): String {
        val withSpaces = s.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return withSpaces.replaceFirstChar { it.uppercase() } + ":"
    }
}
