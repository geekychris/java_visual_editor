package com.visualjava.inspector

import com.visualjava.inspector.PropertyDescriptor.Kind.BOOLEAN
import com.visualjava.inspector.PropertyDescriptor.Kind.IMAGE
import com.visualjava.inspector.PropertyDescriptor.Kind.NUMBER
import com.visualjava.inspector.PropertyDescriptor.Kind.STRING
import com.visualjava.inspector.PropertyDescriptor.Kind.STYLE_CLASS

/** Per-FXML-tag list of editable properties. */
object PropertyCatalog {

    private fun common() = listOf(
        PropertyDescriptor("fx:id", "(Name)", STRING),
        PropertyDescriptor("layoutX", "layoutX", NUMBER),
        PropertyDescriptor("layoutY", "layoutY", NUMBER),
        PropertyDescriptor("prefWidth", "prefWidth", NUMBER),
        PropertyDescriptor("prefHeight", "prefHeight", NUMBER),
        PropertyDescriptor("style", "style", STRING),
        PropertyDescriptor("styleClass", "CSS classes", STYLE_CLASS),
        PropertyDescriptor("visible", "visible", BOOLEAN),
        PropertyDescriptor("disable", "disable", BOOLEAN),
    )

    private val perTag: Map<String, List<PropertyDescriptor>> = mapOf(
        // Buttons
        "Button" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("mnemonicParsing", "mnemonicParsing", BOOLEAN),
        ),
        "ToggleButton" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("selected", "selected", BOOLEAN),
        ),
        "MenuButton" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
        ),
        "Hyperlink" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("visited", "visited", BOOLEAN),
        ),

        // Labels / text
        "Label" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("wrapText", "wrapText", BOOLEAN),
            PropertyDescriptor("alignment", "alignment", STRING),
        ),
        "TextField" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("promptText", "promptText", STRING),
            PropertyDescriptor("editable", "editable", BOOLEAN),
        ),
        "PasswordField" to common() + listOf(
            PropertyDescriptor("promptText", "promptText", STRING),
            PropertyDescriptor("editable", "editable", BOOLEAN),
        ),
        "TextArea" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("promptText", "promptText", STRING),
            PropertyDescriptor("wrapText", "wrapText", BOOLEAN),
            PropertyDescriptor("editable", "editable", BOOLEAN),
        ),

        // Selections
        "CheckBox" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("selected", "selected", BOOLEAN),
        ),
        "RadioButton" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("selected", "selected", BOOLEAN),
        ),
        "ComboBox" to common() + listOf(
            PropertyDescriptor("promptText", "promptText", STRING),
            PropertyDescriptor("editable", "editable", BOOLEAN),
        ),
        "ChoiceBox" to common(),
        "DatePicker" to common() + listOf(
            PropertyDescriptor("promptText", "promptText", STRING),
            PropertyDescriptor("editable", "editable", BOOLEAN),
            PropertyDescriptor("showWeekNumbers", "showWeekNumbers", BOOLEAN),
        ),
        "ColorPicker" to common(),
        "Spinner" to common() + listOf(
            PropertyDescriptor("editable", "editable", BOOLEAN),
        ),
        "Separator" to common() + listOf(
            PropertyDescriptor("orientation", "orientation", STRING),
        ),

        // Lists / trees / tables
        "ListView" to common(),
        "TableView" to common(),
        "TreeView" to common() + listOf(
            PropertyDescriptor("showRoot", "showRoot", BOOLEAN),
        ),
        "TreeTableView" to common() + listOf(
            PropertyDescriptor("showRoot", "showRoot", BOOLEAN),
        ),
        "TabPane" to common() + listOf(
            PropertyDescriptor("side", "side", STRING),
        ),
        "MenuBar" to common() + listOf(
            PropertyDescriptor("useSystemMenuBar", "useSystemMenuBar", BOOLEAN),
        ),

        // Range
        "Slider" to common() + listOf(
            PropertyDescriptor("min", "min", NUMBER),
            PropertyDescriptor("max", "max", NUMBER),
            PropertyDescriptor("value", "value", NUMBER),
            PropertyDescriptor("orientation", "orientation", STRING),
        ),
        "ProgressBar" to common() + listOf(
            PropertyDescriptor("progress", "progress", NUMBER),
        ),
        "ProgressIndicator" to common() + listOf(
            PropertyDescriptor("progress", "progress", NUMBER),
        ),

        // Images
        "ImageView" to listOf(
            PropertyDescriptor("fx:id", "(Name)", STRING),
            PropertyDescriptor("image", "image", IMAGE),
            PropertyDescriptor("layoutX", "layoutX", NUMBER),
            PropertyDescriptor("layoutY", "layoutY", NUMBER),
            PropertyDescriptor("fitWidth", "fitWidth", NUMBER),
            PropertyDescriptor("fitHeight", "fitHeight", NUMBER),
            PropertyDescriptor("preserveRatio", "preserveRatio", BOOLEAN),
            PropertyDescriptor("styleClass", "CSS classes", STYLE_CLASS),
            PropertyDescriptor("visible", "visible", BOOLEAN),
        ),

        // Containers
        "AnchorPane" to common(),
        "Pane" to common(),
        "HBox" to common() + listOf(
            PropertyDescriptor("spacing", "spacing", NUMBER),
            PropertyDescriptor("alignment", "alignment", STRING),
        ),
        "VBox" to common() + listOf(
            PropertyDescriptor("spacing", "spacing", NUMBER),
            PropertyDescriptor("alignment", "alignment", STRING),
        ),
        "FlowPane" to common() + listOf(
            PropertyDescriptor("hgap", "hgap", NUMBER),
            PropertyDescriptor("vgap", "vgap", NUMBER),
            PropertyDescriptor("orientation", "orientation", STRING),
        ),
        "BorderPane" to common(),
        "GridPane" to common() + listOf(
            PropertyDescriptor("hgap", "hgap", NUMBER),
            PropertyDescriptor("vgap", "vgap", NUMBER),
        ),
        "StackPane" to common(),

        // Container-controls
        "ScrollPane" to common() + listOf(
            PropertyDescriptor("fitToWidth", "fitToWidth", BOOLEAN),
            PropertyDescriptor("fitToHeight", "fitToHeight", BOOLEAN),
            PropertyDescriptor("pannable", "pannable", BOOLEAN),
        ),
        "SplitPane" to common() + listOf(
            PropertyDescriptor("orientation", "orientation", STRING),
            PropertyDescriptor("dividerPositions", "dividerPositions", STRING),
        ),
        "TitledPane" to common() + listOf(
            PropertyDescriptor("text", "text", STRING),
            PropertyDescriptor("expanded", "expanded", BOOLEAN),
            PropertyDescriptor("collapsible", "collapsible", BOOLEAN),
            PropertyDescriptor("animated", "animated", BOOLEAN),
        ),
        "Accordion" to common(),
        "ToolBar" to common() + listOf(
            PropertyDescriptor("orientation", "orientation", STRING),
        ),
    )

    fun forTag(tag: String): List<PropertyDescriptor> = perTag[tag] ?: common()
}
