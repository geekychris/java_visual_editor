package com.visualjava.web

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * v2 SCAFFOLD: FXML → server-rendered HTML translator.
 *
 * Covers a useful subset of v1 widgets — enough to demo the dual-output story
 * end to end. NOT a complete v2 build; the full vision is multi-week and
 * involves a Spring controller scaffolder, a Thymeleaf model binder, plus
 * round-tripping back to FXML edits. This file is the foundation other v2
 * pieces will build on.
 *
 * What's implemented:
 *  - Static markup for: Pane/VBox/HBox/AnchorPane/BorderPane (as `<div>`),
 *    Label (`<span>`), Button (`<button>`), TextField/PasswordField (`<input>`),
 *    TextArea (`<textarea>`), CheckBox/RadioButton (`<input type=…>`),
 *    Hyperlink (`<a>`), ImageView (`<img>`), TableView (`<table>`),
 *    MenuBar (`<nav>`).
 *  - fx:id → `id` and `th:object` reference name.
 *  - styleClass → `class`.
 *  - text → text content (with `th:text` if it looks like a binding placeholder).
 *  - onAction → `th:action`/`th:onclick` placeholder.
 *
 * Out of scope here (the v2 punch list):
 *  - Property-binding generation across types.
 *  - CSS adaptation from JavaFX CSS to web CSS.
 *  - Spring controller skeleton with @GetMapping/@PostMapping per form.
 *  - Round-trip from edits to the HTML back to FXML.
 */
object FxmlToHtmlTranslator {

    fun translate(fxmlFile: XmlFile): String = ReadAction.compute<String, RuntimeException> {
        val root = fxmlFile.rootTag ?: return@compute "<!-- empty FXML -->"
        val sb = StringBuilder()
        sb.append("<!doctype html>\n")
        sb.append("<html xmlns:th=\"http://www.thymeleaf.org\">\n")
        sb.append("<head><meta charset=\"utf-8\"><title>")
            .append(fxmlFile.virtualFile?.nameWithoutExtension ?: "Form")
            .append("</title></head>\n")
        sb.append("<body>\n")
        emit(root, sb, depth = 1)
        sb.append("</body>\n</html>\n")
        sb.toString()
    }

    private fun emit(tag: XmlTag, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val fxId = tag.getAttributeValue("fx:id")
        val styleClass = tag.getAttributeValue("styleClass").orEmpty()
        val classAttr = if (styleClass.isNotBlank()) " class=\"$styleClass\"" else ""
        val idAttr = if (fxId != null) " id=\"$fxId\"" else ""

        when (tag.localName) {
            "Pane", "AnchorPane", "VBox", "HBox", "FlowPane", "StackPane", "GridPane", "BorderPane",
            "TitledPane", "ScrollPane", "SplitPane", "ToolBar" -> {
                sb.append(indent).append("<div").append(idAttr).append(classAttr)
                    .append(" data-fx=\"").append(tag.localName).append("\">\n")
                emitChildren(tag, sb, depth + 1)
                sb.append(indent).append("</div>\n")
            }
            "TabPane" -> {
                sb.append(indent).append("<div").append(idAttr).append(classAttr)
                    .append(" data-fx=\"TabPane\" role=\"tablist\">\n")
                emitChildren(tag, sb, depth + 1)
                sb.append(indent).append("</div>\n")
            }
            "Label" -> {
                val text = tag.getAttributeValue("text").orEmpty()
                sb.append(indent).append("<span").append(idAttr).append(classAttr).append(">")
                    .append(escape(text)).append("</span>\n")
            }
            "Button" -> {
                val text = tag.getAttributeValue("text").orEmpty()
                val onAction = tag.getAttributeValue("onAction").orEmpty().removePrefix("#")
                val th = if (onAction.isNotBlank()) " th:onclick=\"|alert('${escape(onAction)}')|\"" else ""
                sb.append(indent).append("<button type=\"button\"").append(idAttr).append(classAttr)
                    .append(th).append(">").append(escape(text)).append("</button>\n")
            }
            "TextField", "PasswordField" -> {
                val type = if (tag.localName == "PasswordField") "password" else "text"
                val placeholder = tag.getAttributeValue("promptText").orEmpty()
                sb.append(indent).append("<input type=\"$type\"").append(idAttr).append(classAttr)
                if (placeholder.isNotBlank()) sb.append(" placeholder=\"").append(escape(placeholder)).append("\"")
                if (fxId != null) sb.append(" name=\"").append(fxId).append("\" th:value=\"\${").append(fxId).append("}\"")
                sb.append(">\n")
            }
            "TextArea" -> {
                sb.append(indent).append("<textarea").append(idAttr).append(classAttr)
                if (fxId != null) sb.append(" name=\"").append(fxId).append("\" th:text=\"\${").append(fxId).append("}\"")
                sb.append("></textarea>\n")
            }
            "CheckBox" -> {
                val text = tag.getAttributeValue("text").orEmpty()
                sb.append(indent).append("<label").append(classAttr).append("><input type=\"checkbox\"")
                    .append(idAttr)
                if (fxId != null) sb.append(" name=\"").append(fxId).append("\"")
                sb.append("> ").append(escape(text)).append("</label>\n")
            }
            "RadioButton" -> {
                val text = tag.getAttributeValue("text").orEmpty()
                sb.append(indent).append("<label").append(classAttr).append("><input type=\"radio\"")
                    .append(idAttr)
                if (fxId != null) sb.append(" name=\"").append(fxId).append("\"")
                sb.append("> ").append(escape(text)).append("</label>\n")
            }
            "Hyperlink" -> {
                val text = tag.getAttributeValue("text").orEmpty()
                sb.append(indent).append("<a href=\"#\"").append(idAttr).append(classAttr).append(">")
                    .append(escape(text)).append("</a>\n")
            }
            "ImageView" -> {
                val src = tag.getAttributeValue("image")?.removePrefix("@").orEmpty()
                sb.append(indent).append("<img").append(idAttr).append(classAttr)
                    .append(" src=\"").append(escape(src)).append("\" alt=\"\">\n")
            }
            "TableView" -> {
                sb.append(indent).append("<table").append(idAttr).append(classAttr).append(">\n")
                val cols = tag.findFirstSubTag("columns")?.subTags
                    ?.filter { it.localName == "TableColumn" } ?: emptyList()
                if (cols.isNotEmpty()) {
                    sb.append(indent).append("  <thead><tr>")
                    for (c in cols) sb.append("<th>").append(escape(c.getAttributeValue("text").orEmpty())).append("</th>")
                    sb.append("</tr></thead>\n")
                }
                if (fxId != null) {
                    sb.append(indent).append("  <tbody><tr th:each=\"row : \${").append(fxId).append("}\">")
                    for (c in cols) {
                        val prop = c.getAttributeValue("fx:id") ?: ""
                        sb.append("<td th:text=\"\${row.").append(prop).append("}\"></td>")
                    }
                    sb.append("</tr></tbody>\n")
                } else {
                    sb.append(indent).append("  <tbody></tbody>\n")
                }
                sb.append(indent).append("</table>\n")
            }
            "MenuBar" -> {
                sb.append(indent).append("<nav").append(idAttr).append(classAttr)
                    .append(" data-fx=\"MenuBar\">\n")
                emitChildren(tag, sb, depth + 1)
                sb.append(indent).append("</nav>\n")
            }
            // Implicit wrappers — pass through to children.
            "children", "menus", "items", "tabs", "panes", "content",
            "top", "bottom", "left", "right", "center" -> emitChildren(tag, sb, depth)
            else -> {
                // Unknown widget: emit a comment so the gap is visible in output.
                sb.append(indent).append("<!-- TODO: translate <").append(tag.localName).append("> to HTML -->\n")
                emitChildren(tag, sb, depth)
            }
        }
    }

    private fun emitChildren(parent: XmlTag, sb: StringBuilder, depth: Int) {
        for (child in parent.subTags) emit(child, sb, depth)
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
