package com.visualjava.events

/**
 * Component-specific example code that gets dropped as a commented body
 * into freshly-generated event handler methods.
 *
 * Each example is a usable cheat-sheet: how to read state, how to change
 * state, common patterns (populate, refresh, validate, …), and what the
 * event object carries. Substitutes the actual `fxId` so it can be pasted
 * by uncommenting any block.
 *
 * We never overwrite an existing method body — these only appear in
 * freshly-generated handlers.
 */
object EventHandlerExamples {

    /** Build the commented body for a (tag, event, fxId) wiring. */
    fun exampleFor(tagName: String, eventProperty: String, fxId: String): String {
        val specific = bodyFor(tagName, eventProperty, fxId)
        val generic = eventCheatSheet(eventProperty)
        val header = "// Sample interactions with $fxId — uncomment what you need:"
        val parts = listOfNotNull(
            header,
            specific.takeIf { it.isNotBlank() },
            generic.takeIf { it.isNotBlank() },
        )
        return parts.joinToString("\n//\n")
    }

    // ─── Per-widget examples ─────────────────────────────────────────────────

    private fun bodyFor(tag: String, evt: String, id: String): String = when (tag) {

        "Button", "MenuButton" -> when (evt) {
            "onAction" -> """
                |// String text = $id.getText();
                |// $id.setText("New label");
                |// $id.setDisable(true);    // grey out
                |// $id.requestFocus();
                |//
                |// // Who fired this event?
                |// Object source = event.getSource();   // → the Button
            """.trimMargin()
            else -> ""
        }

        "ToggleButton" -> when (evt) {
            "onAction" -> """
                |// boolean pressed = $id.isSelected();
                |// $id.setSelected(!pressed);
                |// $id.setText(pressed ? "On" : "Off");
                |//
                |// // If grouped, find the active sibling:
                |// javafx.scene.control.ToggleGroup grp = $id.getToggleGroup();
                |// if (grp != null) {
                |//     javafx.scene.control.Toggle active = grp.getSelectedToggle();
                |// }
            """.trimMargin()
            else -> ""
        }

        "Hyperlink" -> when (evt) {
            "onAction" -> """
                |// String url = $id.getText();
                |// $id.setVisited(true);
                |//
                |// // To actually open the URL you need HostServices from the Application:
                |// // hostServices.showDocument(url);
            """.trimMargin()
            else -> ""
        }

        "CheckBox" -> when (evt) {
            "onAction" -> """
                |// boolean checked = $id.isSelected();
                |// if (checked) {
                |//     // $id was just checked
                |// } else {
                |//     // $id was just unchecked
                |// }
                |//
                |// // Modify state:
                |// $id.setSelected(!checked);
                |// $id.setText("New label");
                |// $id.setIndeterminate(false);
                |// $id.setDisable(true);
            """.trimMargin()
            else -> ""
        }

        "RadioButton" -> when (evt) {
            "onAction" -> """
                |// if ($id.isSelected()) {
                |//     // $id is now the chosen option
                |// }
                |//
                |// // Read which option in the same group is active:
                |// javafx.scene.control.ToggleGroup grp = $id.getToggleGroup();
                |// if (grp != null) {
                |//     javafx.scene.control.Toggle chosen = grp.getSelectedToggle();
                |//     // RadioButton picked = (RadioButton) chosen;
                |// }
            """.trimMargin()
            else -> ""
        }

        "TextField", "PasswordField" -> when (evt) {
            "onAction" -> """
                |// // Enter was pressed — read + validate:
                |// String value = $id.getText();
                |// if (value == null || value.isBlank()) {
                |//     $id.requestFocus();
                |//     return;
                |// }
                |//
                |// // Modify the field:
                |// $id.setText("new value");
                |// $id.clear();
                |// $id.selectAll();
                |// $id.positionCaret(0);
                |// $id.setEditable(false);
            """.trimMargin()
            "onKeyPressed", "onKeyReleased" -> """
                |// String current = $id.getText();
                |//
                |// if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                |//     // Enter pressed in $id
                |// } else if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                |//     $id.clear();
                |// }
            """.trimMargin()
            else -> ""
        }

        "TextArea" -> when (evt) {
            "onKeyPressed", "onKeyReleased", "onKeyTyped" -> """
                |// String text = $id.getText();
                |// int caret = $id.getCaretPosition();
                |//
                |// // Modify:
                |// $id.appendText("\n…");
                |// $id.clear();
                |// $id.setScrollTop(Double.MAX_VALUE);   // jump to bottom
            """.trimMargin()
            else -> ""
        }

        "ComboBox", "ChoiceBox" -> when (evt) {
            "onAction" -> """
                |// // Selected value (null if cleared):
                |// Object selected = $id.getValue();
                |// int index = $id.getSelectionModel().getSelectedIndex();
                |//
                |// // Populate items (usually done once in initialize()):
                |// $id.getItems().setAll("Option A", "Option B", "Option C");
                |//
                |// // Pick programmatically:
                |// $id.getSelectionModel().select(0);
                |// $id.setValue("Option A");
                |// $id.getSelectionModel().clearSelection();
            """.trimMargin()
            else -> ""
        }

        "DatePicker" -> when (evt) {
            "onAction" -> """
                |// java.time.LocalDate picked = $id.getValue();
                |//
                |// // Set programmatically:
                |// $id.setValue(java.time.LocalDate.now());
                |// $id.setPromptText("yyyy-mm-dd");
                |//
                |// // Disable specific days (e.g., no past dates):
                |// $id.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
                |//     @Override
                |//     public void updateItem(java.time.LocalDate date, boolean empty) {
                |//         super.updateItem(date, empty);
                |//         setDisable(empty || date.isBefore(java.time.LocalDate.now()));
                |//     }
                |// });
            """.trimMargin()
            else -> ""
        }

        "ColorPicker" -> when (evt) {
            "onAction" -> """
                |// javafx.scene.paint.Color c = $id.getValue();
                |//
                |// // Convert to CSS hex string:
                |// String hex = String.format("#%02X%02X%02X",
                |//     (int) (c.getRed() * 255),
                |//     (int) (c.getGreen() * 255),
                |//     (int) (c.getBlue() * 255));
                |//
                |// // Apply to another node:
                |// // someNode.setStyle("-fx-background-color: " + hex + ";");
            """.trimMargin()
            else -> ""
        }

        "Spinner" -> when (evt) {
            "onMouseClicked", "onMouseReleased" -> """
                |// Object value = $id.getValue();
                |//
                |// // Configure value factory (do this in initialize() instead):
                |// // $id.setValueFactory(
                |// //     new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 1));
                |// $id.setEditable(true);
            """.trimMargin()
            else -> ""
        }

        "Slider" -> when (evt) {
            "onMouseReleased", "onMouseClicked" -> """
                |// double value = $id.getValue();
                |// double min = $id.getMin();
                |// double max = $id.getMax();
                |//
                |// // Set / configure:
                |// $id.setValue((min + max) / 2.0);
                |// $id.setMajorTickUnit(10);
                |// $id.setShowTickLabels(true);
                |// $id.setSnapToTicks(true);
                |//
                |// // Snap to integer:
                |// $id.setValue(Math.round(value));
            """.trimMargin()
            else -> ""
        }

        "ListView" -> when (evt) {
            "onMouseClicked" -> """
                |// // Selection:
                |// Object selected = $id.getSelectionModel().getSelectedItem();
                |// int index = $id.getSelectionModel().getSelectedIndex();
                |//
                |// // Double-click pattern:
                |// if (event.getClickCount() == 2 && selected != null) {
                |//     // open detail view for `selected`
                |// }
                |//
                |// // Populate items:
                |// $id.getItems().setAll("Item 1", "Item 2", "Item 3");
                |// $id.refresh();
                |//
                |// // Multi-select:
                |// $id.getSelectionModel().setSelectionMode(
                |//     javafx.scene.control.SelectionMode.MULTIPLE);
            """.trimMargin()
            else -> ""
        }

        "TableView" -> when (evt) {
            "onMouseClicked" -> """
                |// Object row = $id.getSelectionModel().getSelectedItem();
                |// int rowIndex = $id.getSelectionModel().getSelectedIndex();
                |//
                |// if (event.getClickCount() == 2 && row != null) {
                |//     // open detail view for `row`
                |// }
                |//
                |// // Re-fill the table:
                |// // $id.setItems(javafx.collections.FXCollections.observableArrayList(yourRows));
                |// $id.refresh();
                |//
                |// // Multi-select rows:
                |// $id.getSelectionModel().setSelectionMode(
                |//     javafx.scene.control.SelectionMode.MULTIPLE);
            """.trimMargin()
            else -> ""
        }

        "TreeView", "TreeTableView" -> when (evt) {
            "onMouseClicked" -> """
                |// javafx.scene.control.TreeItem<?> item = $id.getSelectionModel().getSelectedItem();
                |// if (item != null) {
                |//     Object value = item.getValue();
                |//     boolean expanded = item.isExpanded();
                |//     item.setExpanded(!expanded);
                |// }
                |//
                |// if (event.getClickCount() == 2 && item != null) {
                |//     // double-clicked on this item
                |// }
            """.trimMargin()
            else -> ""
        }

        "TabPane" -> when (evt) {
            "onMouseClicked" -> """
                |// javafx.scene.control.Tab current = $id.getSelectionModel().getSelectedItem();
                |// String tabId = current.getId();
                |// String tabText = current.getText();
                |//
                |// // Switch programmatically:
                |// $id.getSelectionModel().selectFirst();
                |// $id.getSelectionModel().select(2);
                |// $id.getSelectionModel().selectNext();
            """.trimMargin()
            else -> ""
        }

        "ProgressBar", "ProgressIndicator" -> when (evt) {
            "onMouseClicked" -> """
                |// // Read / set progress (0.0 – 1.0, or -1 for indeterminate):
                |// double current = $id.getProgress();
                |// $id.setProgress(0.5);
                |// $id.setProgress(javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);
            """.trimMargin()
            else -> ""
        }

        "ImageView" -> when (evt) {
            "onMouseClicked" -> """
                |// javafx.scene.image.Image img = $id.getImage();
                |//
                |// // Swap the image:
                |// // $id.setImage(new javafx.scene.image.Image(getClass().getResource("/img.png").toExternalForm()));
                |//
                |// // Resize:
                |// $id.setFitWidth(160);
                |// $id.setFitHeight(120);
                |// $id.setPreserveRatio(true);
            """.trimMargin()
            else -> ""
        }

        else -> ""
    }

    // ─── Generic per-event cheat-sheets ─────────────────────────────────────

    private fun eventCheatSheet(evt: String): String = when (evt) {
        "onMouseClicked", "onMousePressed", "onMouseReleased" -> """
            |// MouseEvent details:
            |// double x = event.getX();             // relative to source
            |// double y = event.getY();
            |// javafx.scene.input.MouseButton btn = event.getButton();
            |// int clicks = event.getClickCount();
            |// boolean shift = event.isShiftDown();
            |// boolean ctrl  = event.isControlDown();
            |// boolean alt   = event.isAltDown();
        """.trimMargin()

        "onMouseEntered", "onMouseExited" -> """
            |// MouseEvent details:
            |// double x = event.getX();
            |// double y = event.getY();
        """.trimMargin()

        "onKeyPressed", "onKeyReleased" -> """
            |// KeyEvent details:
            |// javafx.scene.input.KeyCode code = event.getCode();
            |// if (code == javafx.scene.input.KeyCode.ENTER)  { /* … */ }
            |// if (code == javafx.scene.input.KeyCode.ESCAPE) { /* … */ }
            |// boolean shift = event.isShiftDown();
            |// boolean ctrl  = event.isControlDown();
        """.trimMargin()

        "onKeyTyped" -> """
            |// KeyEvent details:
            |// String typed = event.getCharacter();   // the actual character
        """.trimMargin()

        else -> ""
    }
}
