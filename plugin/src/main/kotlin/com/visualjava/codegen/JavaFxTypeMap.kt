package com.visualjava.codegen

/** Maps an FXML element tag to the FQN of the corresponding JavaFX class. */
object JavaFxTypeMap {

    private val table = mapOf(
        // Controls
        "Button" to "javafx.scene.control.Button",
        "ToggleButton" to "javafx.scene.control.ToggleButton",
        "MenuButton" to "javafx.scene.control.MenuButton",
        "SplitMenuButton" to "javafx.scene.control.SplitMenuButton",
        "Label" to "javafx.scene.control.Label",
        "Hyperlink" to "javafx.scene.control.Hyperlink",
        "TextField" to "javafx.scene.control.TextField",
        "PasswordField" to "javafx.scene.control.PasswordField",
        "TextArea" to "javafx.scene.control.TextArea",
        "CheckBox" to "javafx.scene.control.CheckBox",
        "RadioButton" to "javafx.scene.control.RadioButton",
        "ComboBox" to "javafx.scene.control.ComboBox",
        "ChoiceBox" to "javafx.scene.control.ChoiceBox",
        "DatePicker" to "javafx.scene.control.DatePicker",
        "ColorPicker" to "javafx.scene.control.ColorPicker",
        "Spinner" to "javafx.scene.control.Spinner",
        "Slider" to "javafx.scene.control.Slider",
        "Separator" to "javafx.scene.control.Separator",

        // Lists / tables
        "ListView" to "javafx.scene.control.ListView",
        "TableView" to "javafx.scene.control.TableView",
        "TreeView" to "javafx.scene.control.TreeView",
        "TreeTableView" to "javafx.scene.control.TreeTableView",
        "TabPane" to "javafx.scene.control.TabPane",
        "Tab" to "javafx.scene.control.Tab",
        "MenuBar" to "javafx.scene.control.MenuBar",
        "Menu" to "javafx.scene.control.Menu",
        "MenuItem" to "javafx.scene.control.MenuItem",

        // Display
        "ProgressBar" to "javafx.scene.control.ProgressBar",
        "ProgressIndicator" to "javafx.scene.control.ProgressIndicator",
        "ImageView" to "javafx.scene.image.ImageView",

        // Containers
        "AnchorPane" to "javafx.scene.layout.AnchorPane",
        "Pane" to "javafx.scene.layout.Pane",
        "HBox" to "javafx.scene.layout.HBox",
        "VBox" to "javafx.scene.layout.VBox",
        "BorderPane" to "javafx.scene.layout.BorderPane",
        "GridPane" to "javafx.scene.layout.GridPane",
        "StackPane" to "javafx.scene.layout.StackPane",
        "FlowPane" to "javafx.scene.layout.FlowPane",
        "TilePane" to "javafx.scene.layout.TilePane",
        "ScrollPane" to "javafx.scene.control.ScrollPane",
        "SplitPane" to "javafx.scene.control.SplitPane",
        "TitledPane" to "javafx.scene.control.TitledPane",
        "Accordion" to "javafx.scene.control.Accordion",
        "ToolBar" to "javafx.scene.control.ToolBar",
    )

    fun resolve(fxmlTag: String): String = table[fxmlTag] ?: "javafx.scene.Node"
}
