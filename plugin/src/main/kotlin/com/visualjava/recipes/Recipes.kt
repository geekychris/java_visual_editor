package com.visualjava.recipes

/** Registry of every available recipe. */
object Recipes {
    val all: List<Recipe> = listOf(
        CloseWindowRecipe,
        ToggleVisibilityRecipe,
        CheckBoxEnablesTargetRecipe,
        EnableWhenNonEmptyRecipe,
        BindSliderToLabelRecipe,
        OpenFileChooserRecipe,
        RequiredFieldsValidationRecipe,
        ConfirmBeforeActionRecipe,
        GroupRadioButtonsRecipe,
        TabChangeHandlerRecipe,
        ColorPickerBackgroundRecipe,
        FileDropTargetRecipe,
        ListSelectionUpdatesFieldRecipe,
        // Batch A: VB6-feel additions
        MessageBoxRecipe,
        InputBoxRecipe,
        BackgroundTaskRecipe,
        TimerRecipe,
        FormNavigationRecipe,
        StatusBarRecipe,
        ShowModalRecipe,
        AutoPopulateChoicesRecipe,
    )

    fun byId(id: String): Recipe? = all.firstOrNull { it.id == id }
}

/** Capitalize-first helper used to build method names like `closeButton1Window`. */
internal fun String.cap(): String = if (isEmpty()) this else this[0].uppercase() + substring(1)

// ─── Recipe 1: Close Window ──────────────────────────────────────────────────

object CloseWindowRecipe : Recipe {
    override val id = "close-window"
    override val name = "Close Window"
    override val description =
        "Wire the chosen button's onAction to close the form's stage."
    override val roles = listOf(
        RecipeRole(
            "button", "Button",
            "The button that closes the form when clicked.",
            allowedTags = setOf("Button", "MenuButton", "ToggleButton", "Hyperlink"),
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btnFxId = roleAssignments["button"] ?: return
        val btnTag = ctx.nodesByFxId[btnFxId]?.tagName ?: "Button"
        val methodName = "close${btnFxId.cap()}Window"

        ctx.codeGen.ensureField(ctx.controllerClass, btnFxId, btnTag)
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent",
            "((javafx.stage.Stage) $btnFxId.getScene().getWindow()).close();",
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btnFxId, "onAction", methodName)
    }
}

// ─── Recipe 2: Toggle Visibility ─────────────────────────────────────────────

object ToggleVisibilityRecipe : Recipe {
    override val id = "toggle-visibility"
    override val name = "Toggle Visibility"
    override val description =
        "Bind a target's visible property to a toggle's selected state. " +
            "When the toggle is on, the target shows; when off, it hides."
    override val roles = listOf(
        RecipeRole(
            "trigger", "Toggle",
            "CheckBox / ToggleButton / RadioButton that drives visibility.",
            allowedTags = setOf("CheckBox", "ToggleButton", "RadioButton"),
        ),
        RecipeRole(
            "target", "Target",
            "The component whose visibility is bound.",
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val trigger = roleAssignments["trigger"] ?: return
        val target = roleAssignments["target"] ?: return
        val triggerTag = ctx.nodesByFxId[trigger]?.tagName ?: "CheckBox"
        val targetTag = ctx.nodesByFxId[target]?.tagName ?: "javafx.scene.Node"

        ctx.codeGen.ensureField(ctx.controllerClass, trigger, triggerTag)
        ctx.codeGen.ensureField(ctx.controllerClass, target, targetTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(init, "$target.visibleProperty().bind($trigger.selectedProperty())")
    }
}

// ─── Recipe 3: Enable When Non-Empty ─────────────────────────────────────────

object EnableWhenNonEmptyRecipe : Recipe {
    override val id = "enable-when-nonempty"
    override val name = "Enable When Non-Empty"
    override val description =
        "Disable a button until a text field has at least one character."
    override val roles = listOf(
        RecipeRole(
            "source", "Text Field",
            "The field that must be non-empty to enable the target.",
            allowedTags = setOf("TextField", "PasswordField", "TextArea"),
        ),
        RecipeRole(
            "target", "Target Button",
            "The button (or other control) to enable.",
            allowedTags = setOf(
                "Button", "ToggleButton", "MenuButton",
                "CheckBox", "RadioButton", "Hyperlink",
            ),
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val source = roleAssignments["source"] ?: return
        val target = roleAssignments["target"] ?: return
        val sourceTag = ctx.nodesByFxId[source]?.tagName ?: "TextField"
        val targetTag = ctx.nodesByFxId[target]?.tagName ?: "Button"

        ctx.codeGen.ensureField(ctx.controllerClass, source, sourceTag)
        ctx.codeGen.ensureField(ctx.controllerClass, target, targetTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(init, "$target.disableProperty().bind($source.textProperty().isEmpty())")
    }
}

// ─── Recipe 4: Bind Slider to Label ──────────────────────────────────────────

object BindSliderToLabelRecipe : Recipe {
    override val id = "bind-slider-label"
    override val name = "Bind Slider to Label"
    override val description =
        "Show the slider's current value in a label, updated live as the user drags."
    override val roles = listOf(
        RecipeRole(
            "slider", "Slider",
            "The slider whose value is shown.",
            allowedTags = setOf("Slider"),
        ),
        RecipeRole(
            "label", "Label",
            "The label that displays the value.",
            allowedTags = setOf("Label", "TextField"),
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val slider = roleAssignments["slider"] ?: return
        val label = roleAssignments["label"] ?: return
        val labelTag = ctx.nodesByFxId[label]?.tagName ?: "Label"

        ctx.codeGen.ensureField(ctx.controllerClass, slider, "Slider")
        ctx.codeGen.ensureField(ctx.controllerClass, label, labelTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(init, "$label.textProperty().bind($slider.valueProperty().asString(\"%.1f\"))")
    }
}

// ─── Recipe 5: Open File Chooser ─────────────────────────────────────────────

object OpenFileChooserRecipe : Recipe {
    override val id = "open-file-chooser"
    override val name = "Open File Chooser"
    override val description =
        "On button click, show a file-open dialog. If a Text Field is given, " +
            "fill it with the chosen path."
    override val roles = listOf(
        RecipeRole(
            "button", "Button",
            "The button that opens the chooser.",
            allowedTags = setOf("Button", "MenuButton", "Hyperlink"),
        ),
        RecipeRole(
            "target", "Target Field (optional)",
            "Optional text field to receive the chosen path.",
            allowedTags = setOf("TextField"),
            optional = true,
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val target = roleAssignments["target"]
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"
        val methodName = "on${btn.cap()}ChooseFile"

        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        if (target != null) ctx.codeGen.ensureField(ctx.controllerClass, target, "TextField")

        val body = buildString {
            append("javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();\n")
            append("java.io.File picked = chooser.showOpenDialog($btn.getScene().getWindow());\n")
            if (target != null) {
                append("if (picked != null) $target.setText(picked.getAbsolutePath());\n")
            } else {
                append("if (picked != null) { /* TODO: use picked file */ }\n")
            }
        }
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 6: CheckBox Enables Target ───────────────────────────────────────

object CheckBoxEnablesTargetRecipe : Recipe {
    override val id = "checkbox-enables-target"
    override val name = "CheckBox Enables Target"
    override val description =
        "Bind a target's disable property to a toggle's selected state " +
            "(inverted) — target is enabled only when the toggle is checked."
    override val roles = listOf(
        RecipeRole(
            "trigger", "Toggle",
            "CheckBox / ToggleButton / RadioButton that controls the target.",
            allowedTags = setOf("CheckBox", "ToggleButton", "RadioButton"),
        ),
        RecipeRole(
            "target", "Target",
            "The component to enable/disable.",
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val trigger = roleAssignments["trigger"] ?: return
        val target = roleAssignments["target"] ?: return
        val triggerTag = ctx.nodesByFxId[trigger]?.tagName ?: "CheckBox"
        val targetTag = ctx.nodesByFxId[target]?.tagName ?: "javafx.scene.Node"

        ctx.codeGen.ensureField(ctx.controllerClass, trigger, triggerTag)
        ctx.codeGen.ensureField(ctx.controllerClass, target, targetTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(init, "$target.disableProperty().bind($trigger.selectedProperty().not())")
    }
}

// ─── Recipe 7: Required Fields Validation ────────────────────────────────────

object RequiredFieldsValidationRecipe : Recipe {
    override val id = "required-fields-validation"
    override val name = "Required Fields Validation"
    override val description =
        "On button click, check that 1–4 text fields are non-empty. If any are " +
            "blank, show a warning Alert and stop. Otherwise call handleSubmit()."
    override val roles = listOf(
        RecipeRole("button", "Submit Button", "Triggers validation.", setOf("Button")),
        RecipeRole("field1", "Required Field 1", "Required.", setOf("TextField", "PasswordField", "TextArea")),
        RecipeRole("field2", "Required Field 2", "Optional.", setOf("TextField", "PasswordField", "TextArea"), optional = true),
        RecipeRole("field3", "Required Field 3", "Optional.", setOf("TextField", "PasswordField", "TextArea"), optional = true),
        RecipeRole("field4", "Required Field 4", "Optional.", setOf("TextField", "PasswordField", "TextArea"), optional = true),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val fields = listOfNotNull(
            roleAssignments["field1"], roleAssignments["field2"],
            roleAssignments["field3"], roleAssignments["field4"],
        )
        if (fields.isEmpty()) return
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"

        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        for (f in fields) {
            val tag = ctx.nodesByFxId[f]?.tagName ?: "TextField"
            ctx.codeGen.ensureField(ctx.controllerClass, f, tag)
        }

        val emptyCheck = fields.joinToString(" || ") { "$it.getText().isEmpty()" }
        val methodName = "on${btn.cap()}Submit"
        val body = """
            |if ($emptyCheck) {
            |    new javafx.scene.control.Alert(
            |        javafx.scene.control.Alert.AlertType.WARNING,
            |        "Please fill all required fields."
            |    ).showAndWait();
            |    return;
            |}
            |handleSubmit();
        """.trimMargin()
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "handleSubmit", "private void handleSubmit()",
            "// TODO: validation passed — perform submit",
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 8: Confirm Before Action ─────────────────────────────────────────

object ConfirmBeforeActionRecipe : Recipe {
    override val id = "confirm-before-action"
    override val name = "Confirm Before Action"
    override val description =
        "Wrap a button's action with a Yes/No confirmation Alert. Only if " +
            "the user picks Yes do we call the action stub."
    override val roles = listOf(
        RecipeRole("button", "Button", "The button that triggers confirmation.", setOf("Button", "MenuButton", "Hyperlink")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"

        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        val methodName = "on${btn.cap()}Confirm"
        val stubName = "handle${btn.cap()}Confirmed"
        val body = """
            |javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
            |    javafx.scene.control.Alert.AlertType.CONFIRMATION,
            |    "Are you sure?",
            |    javafx.scene.control.ButtonType.YES,
            |    javafx.scene.control.ButtonType.NO
            |);
            |confirm.showAndWait()
            |    .filter(t -> t == javafx.scene.control.ButtonType.YES)
            |    .ifPresent(t -> $stubName());
        """.trimMargin()
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, stubName, "private void $stubName()",
            "// TODO: confirmed action for $btn",
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 9: Group RadioButtons (ToggleGroup) ──────────────────────────────

object GroupRadioButtonsRecipe : Recipe {
    override val id = "group-radio-buttons"
    override val name = "Group RadioButtons"
    override val description =
        "Create a shared ToggleGroup in initialize() and assign 2–4 RadioButtons " +
            "to it, so only one can be selected at a time."
    override val roles = listOf(
        RecipeRole("rb1", "RadioButton 1", "First option.", setOf("RadioButton")),
        RecipeRole("rb2", "RadioButton 2", "Second option.", setOf("RadioButton")),
        RecipeRole("rb3", "RadioButton 3", "Optional.", setOf("RadioButton"), optional = true),
        RecipeRole("rb4", "RadioButton 4", "Optional.", setOf("RadioButton"), optional = true),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val radios = listOfNotNull(
            roleAssignments["rb1"], roleAssignments["rb2"],
            roleAssignments["rb3"], roleAssignments["rb4"],
        )
        if (radios.size < 2) return

        for (r in radios) ctx.codeGen.ensureField(ctx.controllerClass, r, "RadioButton")
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        val groupName = "${radios.first()}Group"
        ctx.codeGen.appendStatement(init, "javafx.scene.control.ToggleGroup $groupName = new javafx.scene.control.ToggleGroup()")
        for (r in radios) ctx.codeGen.appendStatement(init, "$r.setToggleGroup($groupName)")
    }
}

// ─── Recipe 10: Tab Change Handler ───────────────────────────────────────────

object TabChangeHandlerRecipe : Recipe {
    override val id = "tab-change-handler"
    override val name = "Tab Change Handler"
    override val description =
        "Listen for tab-pane selection changes and call onTabChanged(tab) " +
            "whenever the user switches tabs."
    override val roles = listOf(
        RecipeRole("tabPane", "TabPane", "The tab pane to watch.", setOf("TabPane")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val tp = roleAssignments["tabPane"] ?: return
        ctx.codeGen.ensureField(ctx.controllerClass, tp, "TabPane")
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(
            init,
            "$tp.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> on${tp.cap()}Changed(newTab))",
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "on${tp.cap()}Changed",
            "private void on${tp.cap()}Changed(javafx.scene.control.Tab tab)",
            "// TODO: react to tab change. tab may be null if all tabs are removed.",
        )
    }
}

// ─── Recipe 11: Color Picker → Background ────────────────────────────────────

object ColorPickerBackgroundRecipe : Recipe {
    override val id = "colorpicker-background"
    override val name = "Color Picker → Background"
    override val description =
        "Wire a ColorPicker so changing its color updates a target's background " +
            "via inline -fx-background-color style."
    override val roles = listOf(
        RecipeRole("picker", "ColorPicker", "The picker.", setOf("ColorPicker")),
        RecipeRole("target", "Target", "The component whose background to change."),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val picker = roleAssignments["picker"] ?: return
        val target = roleAssignments["target"] ?: return
        val targetTag = ctx.nodesByFxId[target]?.tagName ?: "javafx.scene.Node"

        ctx.codeGen.ensureField(ctx.controllerClass, picker, "ColorPicker")
        ctx.codeGen.ensureField(ctx.controllerClass, target, targetTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(
            init,
            "$picker.valueProperty().addListener((obs, oldColor, newColor) -> " +
                "$target.setStyle(\"-fx-background-color: \" + colorToHex(newColor)))",
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "colorToHex",
            "private String colorToHex(javafx.scene.paint.Color c)",
            """
            |return String.format(
            |    "#%02X%02X%02X",
            |    (int) Math.round(c.getRed() * 255),
            |    (int) Math.round(c.getGreen() * 255),
            |    (int) Math.round(c.getBlue() * 255)
            |);
            """.trimMargin(),
        )
    }
}

// ─── Recipe 12: File Drop Target ─────────────────────────────────────────────

object FileDropTargetRecipe : Recipe {
    override val id = "file-drop-target"
    override val name = "File Drop Target"
    override val description =
        "Make a node accept dragged files from the OS. Each dropped file invokes " +
            "onFileDropped(File) — a stub you fill in."
    override val roles = listOf(
        RecipeRole("target", "Drop Target", "The node that accepts the drop."),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val target = roleAssignments["target"] ?: return
        val tag = ctx.nodesByFxId[target]?.tagName ?: "javafx.scene.Node"
        ctx.codeGen.ensureField(ctx.controllerClass, target, tag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(
            init,
            "$target.setOnDragOver(e -> { " +
                "if (e.getDragboard().hasFiles()) " +
                "e.acceptTransferModes(javafx.scene.input.TransferMode.COPY); " +
                "e.consume(); })",
        )
        ctx.codeGen.appendStatement(
            init,
            "$target.setOnDragDropped(e -> { " +
                "if (e.getDragboard().hasFiles()) { " +
                "for (java.io.File f : e.getDragboard().getFiles()) onFileDropped(f); " +
                "e.setDropCompleted(true); } " +
                "e.consume(); })",
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "onFileDropped",
            "private void onFileDropped(java.io.File file)",
            "// TODO: handle dropped file",
        )
    }
}

// ─── Recipe 13: List Selection Updates Field ─────────────────────────────────

object ListSelectionUpdatesFieldRecipe : Recipe {
    override val id = "list-selection-updates-field"
    override val name = "List Selection → Field"
    override val description =
        "When the user picks an item in a ListView (or TableView / TreeView), " +
            "update a label/text-field with its toString()."
    override val roles = listOf(
        RecipeRole(
            "list", "List / Table / Tree",
            "The selectable source.",
            setOf("ListView", "TableView", "TreeView", "TreeTableView"),
        ),
        RecipeRole(
            "target", "Target",
            "Where to display the selected item.",
            setOf("Label", "TextField", "TextArea"),
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val list = roleAssignments["list"] ?: return
        val target = roleAssignments["target"] ?: return
        val listTag = ctx.nodesByFxId[list]?.tagName ?: "ListView"
        val targetTag = ctx.nodesByFxId[target]?.tagName ?: "Label"

        ctx.codeGen.ensureField(ctx.controllerClass, list, listTag)
        ctx.codeGen.ensureField(ctx.controllerClass, target, targetTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        val setter = if (targetTag in setOf("Label", "TextField", "TextArea")) "setText" else "setText"
        ctx.codeGen.appendStatement(
            init,
            "$list.getSelectionModel().selectedItemProperty().addListener(" +
                "(obs, oldVal, newVal) -> $target.$setter(newVal == null ? \"\" : newVal.toString()))",
        )
    }
}

// ─── Recipe 14: MessageBox (VB6's MsgBox) ────────────────────────────────────

object MessageBoxRecipe : Recipe {
    override val id = "messagebox"
    override val name = "MessageBox (MsgBox)"
    override val description =
        "Wire a button's onAction to show a JavaFX Alert (the VB6 MsgBox). " +
            "Generates an information dialog with title and content text."
    override val roles = listOf(
        RecipeRole("button", "Button", "The button that shows the message.",
            setOf("Button", "MenuButton", "Hyperlink")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"
        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        val methodName = "on${btn.cap()}MessageBox"
        val body = """
            |javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            |    javafx.scene.control.Alert.AlertType.INFORMATION);
            |alert.setTitle("Information");
            |alert.setHeaderText(null);
            |alert.setContentText("Your message here.");
            |alert.showAndWait();
        """.trimMargin()
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 15: InputBox (VB6's InputBox) ────────────────────────────────────

object InputBoxRecipe : Recipe {
    override val id = "inputbox"
    override val name = "InputBox"
    override val description =
        "Wire a button's onAction to show a TextInputDialog (VB6 InputBox). " +
            "If a TextField role is set, the entered value goes there. " +
            "Otherwise generates a stub handler that receives the value."
    override val roles = listOf(
        RecipeRole("button", "Button", "Trigger.",
            setOf("Button", "MenuButton", "Hyperlink")),
        RecipeRole("target", "Target Field (optional)",
            "Where the entered value lands.",
            setOf("TextField", "TextArea", "Label"), optional = true),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val target = roleAssignments["target"]
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"
        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        if (target != null) {
            val tag = ctx.nodesByFxId[target]?.tagName ?: "TextField"
            ctx.codeGen.ensureField(ctx.controllerClass, target, tag)
        }
        val methodName = "on${btn.cap()}InputBox"
        val body = buildString {
            append("javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog();\n")
            append("dlg.setTitle(\"Input\");\n")
            append("dlg.setHeaderText(null);\n")
            append("dlg.setContentText(\"Enter a value:\");\n")
            if (target != null) {
                append("dlg.showAndWait().ifPresent(value -> $target.setText(value));")
            } else {
                append("dlg.showAndWait().ifPresent(value -> handleInputValue(value));")
            }
        }
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        if (target == null) {
            ctx.codeGen.ensurePlainMethod(
                ctx.controllerClass, "handleInputValue",
                "private void handleInputValue(String value)",
                "// TODO: use the entered value",
            )
        }
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 16: Background Task ──────────────────────────────────────────────

object BackgroundTaskRecipe : Recipe {
    override val id = "background-task"
    override val name = "Background Task"
    override val description =
        "Wire a button to run work on a Task<Void>. ProgressBar is bound to " +
            "the task's progressProperty; Label is bound to messageProperty. " +
            "The actual work runs in a doWork(Task) stub you fill in."
    override val roles = listOf(
        RecipeRole("button", "Start Button", "Starts the task.", setOf("Button")),
        RecipeRole("progress", "Progress Bar (optional)",
            "Shows progress.", setOf("ProgressBar", "ProgressIndicator"), optional = true),
        RecipeRole("message", "Message Label (optional)",
            "Displays task message.", setOf("Label", "TextField"), optional = true),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val progress = roleAssignments["progress"]
        val message = roleAssignments["message"]

        ctx.codeGen.ensureField(ctx.controllerClass, btn, "Button")
        if (progress != null) {
            val tag = ctx.nodesByFxId[progress]?.tagName ?: "ProgressBar"
            ctx.codeGen.ensureField(ctx.controllerClass, progress, tag)
        }
        if (message != null) {
            val tag = ctx.nodesByFxId[message]?.tagName ?: "Label"
            ctx.codeGen.ensureField(ctx.controllerClass, message, tag)
        }
        val methodName = "on${btn.cap()}Start"
        val body = buildString {
            append("javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {\n")
            append("    @Override\n")
            append("    protected Void call() throws Exception {\n")
            append("        doWork(this);\n")
            append("        return null;\n")
            append("    }\n")
            append("};\n")
            if (progress != null) append("$progress.progressProperty().bind(task.progressProperty());\n")
            if (message != null) append("$message.textProperty().bind(task.messageProperty());\n")
            append("$btn.setDisable(true);\n")
            append("task.setOnSucceeded(e -> $btn.setDisable(false));\n")
            append("task.setOnFailed(e -> { $btn.setDisable(false); task.getException().printStackTrace(); });\n")
            append("new Thread(task, \"$btn-task\").start();")
        }
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "doWork",
            "private void doWork(javafx.concurrent.Task<?> task) throws Exception",
            """
            |// TODO: long-running work goes here.
            |// Report progress: task.updateProgress(done, total);
            |// Report status:   task.updateMessage("…");
            |// Periodically:    if (task.isCancelled()) return;
            |for (int i = 0; i < 100; i++) {
            |    if (task.isCancelled()) return;
            |    task.updateProgress(i, 100);
            |    task.updateMessage("Step " + i + " of 100");
            |    Thread.sleep(50);
            |}
            """.trimMargin(),
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 17: Timer (periodic action) ──────────────────────────────────────

object TimerRecipe : Recipe {
    override val id = "timer"
    override val name = "Timer (Periodic Action)"
    override val description =
        "Run a method every N milliseconds (the VB6 Timer). The recipe writes " +
            "a Timeline in initialize() and a tick() stub you fill in."
    override val roles = listOf(
        RecipeRole(
            "anchor", "Anchor Component",
            "Any component on this form — used only to scope the field.",
        ),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val anchor = roleAssignments["anchor"] ?: return
        val anchorTag = ctx.nodesByFxId[anchor]?.tagName ?: "javafx.scene.Node"
        ctx.codeGen.ensureField(ctx.controllerClass, anchor, anchorTag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(
            init,
            "javafx.animation.Timeline timer = new javafx.animation.Timeline(" +
                "new javafx.animation.KeyFrame(" +
                "javafx.util.Duration.millis(1000), e -> onTimerTick()))",
        )
        ctx.codeGen.appendStatement(init, "timer.setCycleCount(javafx.animation.Animation.INDEFINITE)")
        ctx.codeGen.appendStatement(init, "timer.play()")
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "onTimerTick",
            "private void onTimerTick()",
            "// TODO: runs every 1000 ms on the JavaFX Application Thread",
        )
    }
}

// ─── Recipe 18: Form Navigation (open another form) ──────────────────────────

object FormNavigationRecipe : Recipe {
    override val id = "form-navigation"
    override val name = "Open Another Form"
    override val description =
        "Wire a button's onAction to load and show another FXML form in a new " +
            "Stage. The target FXML path is generated as a constant you can tweak."
    override val roles = listOf(
        RecipeRole("button", "Button", "Trigger.",
            setOf("Button", "MenuButton", "Hyperlink", "MenuItem")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"
        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        val methodName = "on${btn.cap()}OpenForm"
        val body = """
            |try {
            |    // TODO: change the path to the FXML you want to open.
            |    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
            |        getClass().getResource("/path/to/OtherForm.fxml"));
            |    javafx.scene.Parent root = loader.load();
            |    javafx.stage.Stage stage = new javafx.stage.Stage();
            |    stage.setTitle("Other Form");
            |    stage.setScene(new javafx.scene.Scene(root));
            |    stage.show();
            |} catch (java.io.IOException ex) {
            |    ex.printStackTrace();
            |}
        """.trimMargin()
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 19: Status Bar (setStatus helper) ───────────────────────────────

object StatusBarRecipe : Recipe {
    override val id = "status-bar"
    override val name = "Status Bar"
    override val description =
        "Pick a Label that lives at the bottom of the form; the recipe generates " +
            "a setStatus(String) helper plus a setStatus(String, Duration) variant " +
            "that auto-clears after the given duration."
    override val roles = listOf(
        RecipeRole("label", "Status Label",
            "The label that displays status messages.",
            setOf("Label", "TextField")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val label = roleAssignments["label"] ?: return
        val tag = ctx.nodesByFxId[label]?.tagName ?: "Label"
        ctx.codeGen.ensureField(ctx.controllerClass, label, tag)
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "setStatus",
            "public void setStatus(String message)",
            "$label.setText(message == null ? \"\" : message);",
        )
        ctx.codeGen.ensurePlainMethod(
            ctx.controllerClass, "setStatusFor",
            "public void setStatusFor(String message, javafx.util.Duration duration)",
            """
            |setStatus(message);
            |javafx.animation.Timeline clear = new javafx.animation.Timeline(
            |    new javafx.animation.KeyFrame(duration, e -> setStatus("")));
            |clear.play();
            """.trimMargin(),
        )
    }
}

// ─── Recipe 20: Show Modal (open form as modal dialog) ──────────────────────

object ShowModalRecipe : Recipe {
    override val id = "show-modal"
    override val name = "Show Modal Dialog"
    override val description =
        "Wire a button to open another FXML form as a MODAL dialog. The user " +
            "must close it before returning to this form (Stage.initModality(APPLICATION_MODAL))."
    override val roles = listOf(
        RecipeRole("button", "Button", "Trigger.",
            setOf("Button", "MenuButton", "Hyperlink", "MenuItem")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val btn = roleAssignments["button"] ?: return
        val btnTag = ctx.nodesByFxId[btn]?.tagName ?: "Button"
        ctx.codeGen.ensureField(ctx.controllerClass, btn, btnTag)
        val methodName = "on${btn.cap()}OpenModal"
        val body = """
            |try {
            |    // TODO: change the path to the FXML you want to open.
            |    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
            |        getClass().getResource("/path/to/DialogForm.fxml"));
            |    javafx.scene.Parent root = loader.load();
            |    javafx.stage.Stage dialog = new javafx.stage.Stage();
            |    dialog.setTitle("Dialog");
            |    dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            |    dialog.initOwner($btn.getScene().getWindow());
            |    dialog.setScene(new javafx.scene.Scene(root));
            |    dialog.showAndWait();
            |    // dialog returned — `loader.getController()` can give you values picked
            |} catch (java.io.IOException ex) {
            |    ex.printStackTrace();
            |}
        """.trimMargin()
        ctx.codeGen.ensureHandlerWithBody(
            ctx.controllerClass, methodName, "javafx.event.ActionEvent", body,
        )
        ctx.codeGen.wireFxmlEvent(ctx.fxmlFile, btn, "onAction", methodName)
    }
}

// ─── Recipe 21: Auto-Populate ComboBox/ChoiceBox/ListView ───────────────────

object AutoPopulateChoicesRecipe : Recipe {
    override val id = "auto-populate-choices"
    override val name = "Auto-Populate Choices"
    override val description =
        "Fill a ComboBox, ChoiceBox, or ListView with a hard-coded list of " +
            "starter items in initialize() — quicker than typing FXML."
    override val roles = listOf(
        RecipeRole("control", "Choice Control",
            "The control to populate.",
            setOf("ComboBox", "ChoiceBox", "ListView")),
    )

    override fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>) {
        val ctl = roleAssignments["control"] ?: return
        val tag = ctx.nodesByFxId[ctl]?.tagName ?: "ComboBox"
        ctx.codeGen.ensureField(ctx.controllerClass, ctl, tag)
        val init = ctx.codeGen.ensureInitialize(ctx.controllerClass)
        ctx.codeGen.appendStatement(
            init,
            "$ctl.getItems().setAll(\"Option A\", \"Option B\", \"Option C\")",
        )
    }
}
