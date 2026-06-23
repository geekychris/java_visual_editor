package com.visualjava.help

/**
 * Reference docs for palette widgets. Entries are hand-curated where the
 * properties / events warrant explanation; widgets without an entry fall back
 * to a stub generated from the palette catalog (summary + Oracle link).
 */
object ComponentDocsCatalog {

    private const val JFX = "https://openjfx.io/javadoc/21"
    private const val CONTROL = "$JFX/javafx.controls/javafx/scene/control"
    private const val LAYOUT = "$JFX/javafx.graphics/javafx/scene/layout"
    private const val IMAGE = "$JFX/javafx.graphics/javafx/scene/image"
    private const val CHART = "$JFX/javafx.controls/javafx/scene/chart"

    private val entries: Map<String, ComponentDoc> = listOf(
        // --- Containers --------------------------------------------------------
        ComponentDoc(
            "AnchorPane",
            "Free-form container; pins children to one or more edges by setting AnchorPane.topAnchor / leftAnchor / etc.",
            """
            <AnchorPane fx:id="root" prefWidth="400" prefHeight="300">
              <children>
                <Button fx:id="okBtn" text="OK"
                        AnchorPane.bottomAnchor="10" AnchorPane.rightAnchor="10"/>
              </children>
            </AnchorPane>
            """.trimIndent(),
            commonProperties = listOf(
                "prefWidth/prefHeight" to "Preferred size; ignored if a parent enforces a different size",
                "AnchorPane.{top,bottom,left,right}Anchor" to "Per-child pinning to a parent edge (in px)",
            ),
            javadocUrl = "$LAYOUT/AnchorPane.html",
        ),
        ComponentDoc(
            "HBox",
            "Horizontal box layout; children flow left-to-right with optional spacing.",
            """
            <HBox fx:id="row" spacing="8" alignment="CENTER_LEFT">
              <children>
                <Label text="Name:"/>
                <TextField fx:id="nameField" HBox.hgrow="ALWAYS"/>
                <Button fx:id="saveBtn" text="Save"/>
              </children>
            </HBox>
            """.trimIndent(),
            commonProperties = listOf(
                "spacing" to "Gap between children, in px",
                "alignment" to "TOP_LEFT / CENTER / BOTTOM_RIGHT etc. (use TOP_LEFT for default)",
                "HBox.hgrow" to "Per-child: ALWAYS / SOMETIMES / NEVER — controls horizontal stretch",
            ),
            javadocUrl = "$LAYOUT/HBox.html",
        ),
        ComponentDoc(
            "VBox",
            "Vertical box layout; children stack top-to-bottom with optional spacing.",
            """
            <VBox fx:id="form" spacing="8" alignment="TOP_LEFT">
              <children>
                <Label text="Login"/>
                <TextField fx:id="username" promptText="username"/>
                <PasswordField fx:id="password" promptText="password"/>
                <Button fx:id="loginBtn" text="Login"/>
              </children>
            </VBox>
            """.trimIndent(),
            commonProperties = listOf(
                "spacing" to "Gap between children, in px",
                "VBox.vgrow" to "Per-child stretch policy",
            ),
            javadocUrl = "$LAYOUT/VBox.html",
        ),
        ComponentDoc(
            "BorderPane",
            "5-slot layout: top / bottom / left / right / center. Each slot holds one node.",
            """
            <BorderPane fx:id="root">
              <top>    <MenuBar fx:id="menuBar"/> </top>
              <left>   <VBox fx:id="sidebar" prefWidth="180"/> </left>
              <center> <ScrollPane fitToWidth="true"/> </center>
              <bottom> <Label text="Ready"/> </bottom>
            </BorderPane>
            """.trimIndent(),
            commonProperties = listOf(
                "top/bottom/left/right/center" to "Each accepts a single child node",
            ),
            javadocUrl = "$LAYOUT/BorderPane.html",
        ),
        ComponentDoc(
            "GridPane",
            "Spreadsheet-style layout; each child specifies its column/row via GridPane.columnIndex/rowIndex.",
            """
            <GridPane hgap="8" vgap="6">
              <children>
                <Label text="Name"  GridPane.columnIndex="0" GridPane.rowIndex="0"/>
                <TextField           GridPane.columnIndex="1" GridPane.rowIndex="0"/>
                <Label text="Email" GridPane.columnIndex="0" GridPane.rowIndex="1"/>
                <TextField           GridPane.columnIndex="1" GridPane.rowIndex="1"/>
              </children>
            </GridPane>
            """.trimIndent(),
            commonProperties = listOf(
                "hgap/vgap" to "Inter-column / inter-row spacing",
                "GridPane.columnIndex/rowIndex" to "Per-child cell coordinates (0-based)",
                "GridPane.columnSpan/rowSpan" to "Span multiple cells",
            ),
            javadocUrl = "$LAYOUT/GridPane.html",
        ),
        ComponentDoc(
            "StackPane",
            "Z-stacked layout; children pile on top of each other. Useful for overlay UIs.",
            """
            <StackPane>
              <children>
                <ImageView fx:id="bg"/>
                <Label text="Overlay text" StackPane.alignment="BOTTOM_RIGHT"/>
              </children>
            </StackPane>
            """.trimIndent(),
            javadocUrl = "$LAYOUT/StackPane.html",
        ),
        ComponentDoc(
            "TabPane",
            "Tabbed container — each Tab holds one content node.",
            """
            <TabPane>
              <tabs>
                <Tab text="General"><content>
                  <VBox><children><Label text="General settings"/></children></VBox>
                </content></Tab>
                <Tab text="Advanced"><content>
                  <VBox><children><Label text="Advanced settings"/></children></VBox>
                </content></Tab>
              </tabs>
            </TabPane>
            """.trimIndent(),
            commonProperties = listOf(
                "side" to "TOP (default) / BOTTOM / LEFT / RIGHT",
                "tabClosingPolicy" to "ALL_TABS / SELECTED_TAB / UNAVAILABLE",
            ),
            javadocUrl = "$CONTROL/TabPane.html",
        ),
        ComponentDoc(
            "ScrollPane",
            "Wraps a single content node and shows scrollbars if it overflows.",
            """
            <ScrollPane fitToWidth="true">
              <content>
                <VBox spacing="4">
                  <children>
                    <Label text="Long content…"/>
                  </children>
                </VBox>
              </content>
            </ScrollPane>
            """.trimIndent(),
            commonProperties = listOf(
                "fitToWidth/fitToHeight" to "Stretch content to viewport size",
                "pannable" to "Drag to scroll",
                "hbarPolicy/vbarPolicy" to "ALWAYS / AS_NEEDED / NEVER",
            ),
            javadocUrl = "$CONTROL/ScrollPane.html",
        ),
        ComponentDoc(
            "SplitPane",
            "Resizable splitter; items[] are the panes, dividerPositions sets the initial split.",
            """
            <SplitPane orientation="HORIZONTAL" dividerPositions="0.3">
              <items>
                <VBox><children><Label text="Sidebar"/></children></VBox>
                <VBox><children><Label text="Main"/></children></VBox>
              </items>
            </SplitPane>
            """.trimIndent(),
            javadocUrl = "$CONTROL/SplitPane.html",
        ),

        // --- Controls ----------------------------------------------------------
        ComponentDoc(
            "Button",
            "Clickable button. Wire its onAction to the controller method that runs when clicked.",
            """
            <Button fx:id="saveBtn" text="Save" defaultButton="true" onAction="#onSave"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private Button saveBtn;

            @FXML
            private void onSave(ActionEvent event) {
                saveBtn.setDisable(true);             // prevent double-submit
                saveBtn.setText("Saving…");
                doBackgroundSave().whenComplete((r, err) -> Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    saveBtn.setText("Save");
                }));
            }
            """.trimIndent(),
            commonProperties = listOf(
                "text" to "Button label",
                "defaultButton" to "true → activates on Enter even when not focused",
                "cancelButton" to "true → activates on Esc",
                "mnemonicParsing" to "true → underscore in text becomes Alt-shortcut (\"_Save\")",
                "graphic" to "Optional icon node (ImageView/SVG)",
            ),
            commonEvents = listOf(
                "onAction" to "Fired on click, Space (when focused), Enter (if defaultButton)",
            ),
            javadocUrl = "$CONTROL/Button.html",
        ),
        ComponentDoc(
            "ToggleButton",
            "Like Button but stays pressed. Join into a group via ToggleGroup for radio-style exclusivity.",
            """
            <ToggleButton fx:id="boldBtn" text="B" selected="false"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ToggleButton boldBtn;

            @FXML
            public void initialize() {
                boldBtn.selectedProperty().addListener((obs, was, isOn) -> {
                    System.out.println("Bold is now " + (isOn ? "ON" : "off"));
                });
            }
            """.trimIndent(),
            commonProperties = listOf(
                "selected" to "The on/off state",
                "toggleGroup" to "Reference to a <ToggleGroup fx:id=\"...\"/> for exclusive selection",
            ),
            javadocUrl = "$CONTROL/ToggleButton.html",
        ),
        ComponentDoc(
            "Label",
            "Read-only text. Cheap and the right tool when you don't need user input.",
            """
            <Label fx:id="statusLabel" text="Ready" wrapText="true"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private Label statusLabel;

            private void showStatus(String message) {
                // Always update UI on the FX thread:
                Platform.runLater(() -> statusLabel.setText(message));
            }
            """.trimIndent(),
            commonProperties = listOf(
                "text" to "Label content",
                "wrapText" to "true → wraps long text instead of clipping",
                "graphic" to "Optional icon node",
                "labelFor" to "Reference to an Input — clicking the label focuses the input",
            ),
            javadocUrl = "$CONTROL/Label.html",
        ),
        ComponentDoc(
            "TextField",
            "Single-line text input.",
            """
            <TextField fx:id="emailField" promptText="email@example.com" onAction="#onSubmit"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private TextField emailField;

            @FXML
            public void initialize() {
                // Live validation on every keystroke
                emailField.textProperty().addListener((obs, oldText, newText) -> {
                    boolean valid = newText.matches(".+@.+\\..+");
                    emailField.setStyle(valid ? "" : "-fx-border-color: red;");
                });
            }

            @FXML
            private void onSubmit(ActionEvent event) {
                String email = emailField.getText();
                System.out.println("submit: " + email);
            }
            """.trimIndent(),
            commonProperties = listOf(
                "text" to "Current value (read this in the controller)",
                "promptText" to "Placeholder shown when empty",
                "editable" to "false → display-only",
                "prefColumnCount" to "Hint for preferred width in character cells",
            ),
            commonEvents = listOf(
                "onAction" to "Fired on Enter — useful for inline-submit forms",
            ),
            javadocUrl = "$CONTROL/TextField.html",
        ),
        ComponentDoc(
            "PasswordField",
            "TextField that masks input. Read the typed value via getText() / .text in FXML.",
            """
            <PasswordField fx:id="passwordField" promptText="password"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private PasswordField passwordField;

            @FXML
            private void onLogin(ActionEvent event) {
                char[] pw = passwordField.getText().toCharArray();
                try {
                    if (authenticate(pw)) {
                        // ...
                    }
                } finally {
                    // Best-effort wipe — JavaFX doesn't expose getChars()
                    passwordField.clear();
                }
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/PasswordField.html",
        ),
        ComponentDoc(
            "TextArea",
            "Multi-line text input. wrapText is usually what you want.",
            """
            <TextArea fx:id="notes" wrapText="true" prefRowCount="6" promptText="Notes…"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private TextArea notes;

            @FXML
            public void initialize() {
                // Auto-save 500ms after the user stops typing
                PauseTransition debounce = new PauseTransition(Duration.millis(500));
                notes.textProperty().addListener((obs, was, now) -> {
                    debounce.setOnFinished(e -> persistNotes(now));
                    debounce.playFromStart();
                });
            }
            """.trimIndent(),
            commonProperties = listOf(
                "wrapText" to "true → soft-wrap instead of horizontal scroll",
                "prefRowCount/prefColumnCount" to "Initial size in character cells",
            ),
            javadocUrl = "$CONTROL/TextArea.html",
        ),
        ComponentDoc(
            "CheckBox",
            "Binary toggle. Use indeterminate for tri-state (e.g. \"partial selection\").",
            """
            <CheckBox fx:id="agreeCheck" text="I agree to the terms" onAction="#onAgreeChanged"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private CheckBox agreeCheck;
            @FXML private Button submitBtn;

            @FXML
            public void initialize() {
                // Disable submit until the box is ticked
                submitBtn.disableProperty().bind(agreeCheck.selectedProperty().not());
            }

            @FXML
            private void onAgreeChanged(ActionEvent event) {
                System.out.println("agreed = " + agreeCheck.isSelected());
            }
            """.trimIndent(),
            commonProperties = listOf(
                "selected" to "true/false",
                "indeterminate" to "Third state for tri-state checkboxes",
                "allowIndeterminate" to "Click cycles through three states",
            ),
            javadocUrl = "$CONTROL/CheckBox.html",
        ),
        ComponentDoc(
            "RadioButton",
            "Exclusive choice within a ToggleGroup. Always create the group too.",
            """
            <fx:define>
              <ToggleGroup fx:id="sizeGroup"/>
            </fx:define>
            <VBox spacing="4">
              <children>
                <RadioButton text="Small"  toggleGroup="${'$'}sizeGroup" userData="S"/>
                <RadioButton text="Medium" toggleGroup="${'$'}sizeGroup" userData="M" selected="true"/>
                <RadioButton text="Large"  toggleGroup="${'$'}sizeGroup" userData="L"/>
              </children>
            </VBox>
            """.trimIndent(),
            controllerExample = """
            @FXML private ToggleGroup sizeGroup;

            @FXML
            public void initialize() {
                sizeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
                    if (newT == null) return;
                    String size = (String) newT.getUserData();   // "S" / "M" / "L"
                    System.out.println("size = " + size);
                });
            }
            """.trimIndent(),
            commonProperties = listOf(
                "toggleGroup" to "Reference to the ToggleGroup (\$groupId)",
                "userData" to "Optional opaque value per option — read in the handler",
            ),
            javadocUrl = "$CONTROL/RadioButton.html",
        ),
        ComponentDoc(
            "ComboBox",
            "Dropdown picker. Set items[] in the controller (FXML can't easily populate non-trivial lists).",
            """
            <ComboBox fx:id="countryCombo" promptText="Pick a country" editable="false"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ComboBox<String> countryCombo;

            @FXML
            public void initialize() {
                countryCombo.getItems().setAll("USA", "Canada", "Mexico", "Brazil");
                countryCombo.getSelectionModel().selectedItemProperty()
                    .addListener((obs, was, now) -> System.out.println("picked: " + now));
            }
            """.trimIndent(),
            commonProperties = listOf(
                "editable" to "true → allows typing a free-form value",
                "promptText" to "Placeholder when nothing is selected",
                "visibleRowCount" to "Rows shown in the dropdown",
            ),
            javadocUrl = "$CONTROL/ComboBox.html",
        ),
        ComponentDoc(
            "DatePicker",
            "Combo with a calendar popup.",
            """
            <DatePicker fx:id="birthday" promptText="yyyy-mm-dd" showWeekNumbers="true"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private DatePicker birthday;

            @FXML
            public void initialize() {
                birthday.setValue(LocalDate.now().minusYears(18));
                birthday.valueProperty().addListener((obs, was, now) -> {
                    if (now != null) System.out.println("DOB: " + now);
                });
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/DatePicker.html",
        ),
        ComponentDoc(
            "ColorPicker",
            "Combo with a color picker popup. value property returns javafx.scene.paint.Color.",
            """
            <ColorPicker fx:id="textColor" value="BLUE"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ColorPicker textColor;
            @FXML private Label preview;

            @FXML
            public void initialize() {
                textColor.valueProperty().addListener((obs, was, c) -> {
                    preview.setTextFill(c);
                });
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/ColorPicker.html",
        ),
        ComponentDoc(
            "Slider",
            "Drag-handle for picking a value within a range.",
            """
            <Slider fx:id="volumeSlider" min="0" max="100" value="50"
                    showTickLabels="true" showTickMarks="true" majorTickUnit="25"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private Slider volumeSlider;
            @FXML private Label volumeLabel;

            @FXML
            public void initialize() {
                volumeLabel.textProperty().bind(
                    volumeSlider.valueProperty().asString("Volume: %.0f%%")
                );
            }
            """.trimIndent(),
            commonProperties = listOf(
                "min/max/value" to "Range and current value",
                "showTickLabels/showTickMarks" to "Visual tick aids",
                "majorTickUnit" to "Step between major tick marks",
                "snapToTicks" to "Lock value to tick positions",
            ),
            javadocUrl = "$CONTROL/Slider.html",
        ),
        ComponentDoc(
            "Spinner",
            "Up/down number picker. Set the value factory in the controller (IntegerSpinnerValueFactory, etc.).",
            """
            <Spinner fx:id="quantity" editable="true"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private Spinner<Integer> quantity;

            @FXML
            public void initialize() {
                quantity.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 1, 1)
                );
                quantity.valueProperty().addListener((obs, was, now) -> {
                    System.out.println("qty: " + now);
                });
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/Spinner.html",
        ),
        ComponentDoc(
            "Hyperlink",
            "Click-styled link. Wire onAction (NOT onClick).",
            """
            <Hyperlink fx:id="docsLink" text="View docs" onAction="#openDocs"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private Hyperlink docsLink;

            @FXML
            private void openDocs(ActionEvent event) {
                getHostServices().showDocument("https://example.com/docs");
                docsLink.setVisited(true);
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/Hyperlink.html",
        ),

        // --- Lists / tables ----------------------------------------------------
        ComponentDoc(
            "ListView",
            "Single-column list of items. Populate items[] from the controller.",
            """
            <ListView fx:id="filesList" prefHeight="200"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ListView<String> filesList;

            @FXML
            public void initialize() {
                filesList.setItems(FXCollections.observableArrayList(
                    "report.pdf", "budget.xlsx", "notes.txt"));
                filesList.getSelectionModel().selectedItemProperty()
                    .addListener((obs, was, now) -> System.out.println("opened: " + now));
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/ListView.html",
        ),
        ComponentDoc(
            "TableView",
            "Multi-column table. Define columns in FXML; bind cellValueFactory via PropertyValueFactory in the controller.",
            """
            <TableView fx:id="peopleTable">
              <columns>
                <TableColumn fx:id="nameCol"  text="Name"  prefWidth="180"/>
                <TableColumn fx:id="ageCol"   text="Age"   prefWidth="60"/>
                <TableColumn fx:id="emailCol" text="Email" prefWidth="200"/>
              </columns>
            </TableView>
            """.trimIndent(),
            controllerExample = """
            // Java bean (use a real one or a record; needs public getters).
            public record Person(String name, int age, String email) {}

            @FXML private TableView<Person> peopleTable;
            @FXML private TableColumn<Person, String>  nameCol;
            @FXML private TableColumn<Person, Number>  ageCol;
            @FXML private TableColumn<Person, String>  emailCol;

            @FXML
            public void initialize() {
                nameCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name()));
                ageCol .setCellValueFactory(d -> new ReadOnlyIntegerWrapper(d.getValue().age()));
                emailCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().email()));
                peopleTable.setItems(FXCollections.observableArrayList(
                    new Person("Ada Lovelace",   36, "ada@example.com"),
                    new Person("Alan Turing",    41, "alan@example.com")
                ));
            }
            """.trimIndent(),
            commonProperties = listOf(
                "columns" to "List of TableColumn definitions (set fx:id + text + prefWidth)",
                "items" to "Bind in the controller via setItems(observableList)",
                "editable" to "true → cells can be edited if cellFactory supports it",
            ),
            javadocUrl = "$CONTROL/TableView.html",
            tutorial = "https://docs.oracle.com/javafx/2/ui_controls/table-view.htm",
        ),
        ComponentDoc(
            "TreeView",
            "Hierarchical list. Build the root TreeItem in the controller and call setRoot().",
            """
            <TreeView fx:id="navTree" showRoot="false"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private TreeView<String> navTree;

            @FXML
            public void initialize() {
                TreeItem<String> root = new TreeItem<>("Root");
                TreeItem<String> docs = new TreeItem<>("Documents");
                docs.getChildren().addAll(new TreeItem<>("a.txt"), new TreeItem<>("b.txt"));
                root.getChildren().add(docs);
                root.setExpanded(true);
                navTree.setRoot(root);
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/TreeView.html",
        ),
        ComponentDoc(
            "MenuBar",
            "Top-of-window menu strip. Use the Menu Editor (Cmd+Shift+A → Menu Editor) to build it visually.",
            """
            <MenuBar fx:id="menuBar">
              <menus>
                <Menu text="File">
                  <items>
                    <MenuItem text="New"   onAction="#onNew"/>
                    <MenuItem text="Open…" onAction="#onOpen"/>
                  </items>
                </Menu>
              </menus>
            </MenuBar>
            """.trimIndent(),
            controllerExample = """
            @FXML private MenuBar menuBar;

            @FXML
            public void initialize() {
                // macOS only — integrate with the system menu bar at the top of the screen.
                if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    menuBar.setUseSystemMenuBar(true);
                }
            }

            @FXML private void onNew (ActionEvent e) { /* … */ }
            @FXML private void onOpen(ActionEvent e) {
                FileChooser fc = new FileChooser();
                File picked = fc.showOpenDialog(menuBar.getScene().getWindow());
            }
            """.trimIndent(),
            commonProperties = listOf(
                "useSystemMenuBar" to "true on macOS → integrates with the system menu bar at the top of the screen",
            ),
            javadocUrl = "$CONTROL/MenuBar.html",
        ),

        // --- Display -----------------------------------------------------------
        ComponentDoc(
            "ProgressBar",
            "Horizontal bar showing 0..1 progress. Set progress to -1 for indeterminate.",
            """
            <ProgressBar fx:id="uploadBar" prefWidth="200" progress="0.42"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ProgressBar uploadBar;

            private void uploadFile(File f) {
                Task<Void> task = new Task<>() {
                    @Override protected Void call() {
                        for (int i = 0; i <= 100; i++) {
                            updateProgress(i, 100);
                            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                        }
                        return null;
                    }
                };
                uploadBar.progressProperty().bind(task.progressProperty());
                new Thread(task, "upload").start();
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/ProgressBar.html",
        ),
        ComponentDoc(
            "ProgressIndicator",
            "Spinning indicator. -1 progress = indeterminate; otherwise shows a fill ring.",
            """
            <ProgressIndicator fx:id="spinner" progress="-1"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private ProgressIndicator spinner;

            private void runWithSpinner(Runnable work) {
                spinner.setVisible(true);
                Task<Void> task = new Task<>() {
                    @Override protected Void call() { work.run(); return null; }
                };
                task.setOnSucceeded(e -> spinner.setVisible(false));
                task.setOnFailed   (e -> spinner.setVisible(false));
                new Thread(task).start();
            }
            """.trimIndent(),
            javadocUrl = "$CONTROL/ProgressIndicator.html",
        ),
        ComponentDoc(
            "ImageView",
            "Renders an Image. Use the Browse button on the image property to pick a file from the project.",
            """
            <ImageView fx:id="avatar" fitWidth="64" fitHeight="64" preserveRatio="true">
              <image><Image url="@/avatar.png"/></image>
            </ImageView>
            """.trimIndent(),
            controllerExample = """
            @FXML private ImageView avatar;

            private void loadAvatar(String url) {
                // background load — UI stays responsive on big images
                Image img = new Image(url, /*backgroundLoading=*/ true);
                avatar.setImage(img);
            }
            """.trimIndent(),
            commonProperties = listOf(
                "image" to "Image URL — @-prefixed = relative to the FXML file's package",
                "fitWidth/fitHeight" to "Resize to fit this box",
                "preserveRatio" to "Maintain aspect ratio when resized",
                "smooth" to "Higher-quality downscale (slightly slower)",
            ),
            javadocUrl = "$IMAGE/ImageView.html",
        ),
        ComponentDoc(
            "WebView",
            "Embedded browser (WebKit). Requires `javafx.web` module on the runtime classpath.",
            """
            <WebView fx:id="browser" prefWidth="640" prefHeight="480"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private WebView browser;

            @FXML
            public void initialize() {
                WebEngine engine = browser.getEngine();
                engine.load("https://example.com");
                engine.titleProperty().addListener((obs, was, title) -> {
                    System.out.println("title: " + title);
                });
            }
            """.trimIndent(),
            commonProperties = listOf(
                "fontScale" to "Zoom factor for text",
                "zoom" to "Whole-page zoom",
            ),
            javadocUrl = "$JFX/javafx.web/javafx/scene/web/WebView.html",
        ),
        ComponentDoc(
            "HTMLEditor",
            "Rich-text WYSIWYG editor producing HTML. Requires `javafx.web` module on the runtime classpath.",
            """
            <HTMLEditor fx:id="bodyEditor" prefWidth="600" prefHeight="300"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private HTMLEditor bodyEditor;

            @FXML
            public void initialize() {
                bodyEditor.setHtmlText("<p>Type your message here…</p>");
            }

            @FXML
            private void onSend(ActionEvent event) {
                String html = bodyEditor.getHtmlText();
                sendEmail(html);
            }
            """.trimIndent(),
            commonProperties = listOf(
                "htmlText" to "Get/set the contents as HTML",
            ),
            javadocUrl = "$JFX/javafx.web/javafx/scene/web/HTMLEditor.html",
        ),
        ComponentDoc(
            "MediaView",
            "Plays audio/video via a MediaPlayer. Requires `javafx.media` module on the runtime classpath.",
            """
            <MediaView fx:id="videoPane" fitWidth="640" fitHeight="360" preserveRatio="true"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private MediaView videoPane;

            @FXML
            public void initialize() {
                Media media = new Media(getClass().getResource("/clip.mp4").toExternalForm());
                MediaPlayer player = new MediaPlayer(media);
                player.setAutoPlay(false);
                videoPane.setMediaPlayer(player);
            }

            @FXML
            private void onPlay(ActionEvent event) {
                videoPane.getMediaPlayer().play();
            }
            """.trimIndent(),
            commonProperties = listOf(
                "mediaPlayer" to "Set in the controller via setMediaPlayer(new MediaPlayer(media))",
            ),
            javadocUrl = "$JFX/javafx.media/javafx/scene/media/MediaView.html",
        ),
        ComponentDoc(
            "LineChart",
            "Line graph. Requires xAxis + yAxis nodes (NumberAxis or CategoryAxis).",
            """
            <LineChart fx:id="sales">
              <xAxis><NumberAxis label="Quarter"/></xAxis>
              <yAxis><NumberAxis label="Revenue"/></yAxis>
            </LineChart>
            """.trimIndent(),
            controllerExample = """
            @FXML private LineChart<Number, Number> sales;

            @FXML
            public void initialize() {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName("FY24");
                series.getData().add(new XYChart.Data<>(1,  120));
                series.getData().add(new XYChart.Data<>(2,  180));
                series.getData().add(new XYChart.Data<>(3,  140));
                series.getData().add(new XYChart.Data<>(4,  220));
                sales.getData().add(series);
            }
            """.trimIndent(),
            commonProperties = listOf(
                "title" to "Chart title",
                "data" to "Populate in the controller via getData().add(new XYChart.Series<>(...))",
                "legendSide" to "TOP / BOTTOM / LEFT / RIGHT",
            ),
            javadocUrl = "$CHART/LineChart.html",
        ),
        ComponentDoc(
            "BarChart",
            "Bar chart — requires a CategoryAxis on x and NumberAxis on y (or vice versa).",
            """
            <BarChart fx:id="salesBars">
              <xAxis><CategoryAxis label="Region"/></xAxis>
              <yAxis><NumberAxis label="Revenue"/></yAxis>
            </BarChart>
            """.trimIndent(),
            controllerExample = """
            @FXML private BarChart<String, Number> salesBars;

            @FXML
            public void initialize() {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Q4");
                series.getData().add(new XYChart.Data<>("North", 230));
                series.getData().add(new XYChart.Data<>("South", 180));
                series.getData().add(new XYChart.Data<>("East",  290));
                series.getData().add(new XYChart.Data<>("West",  140));
                salesBars.getData().add(series);
            }
            """.trimIndent(),
            javadocUrl = "$CHART/BarChart.html",
        ),
        ComponentDoc(
            "PieChart",
            "Pie chart of PieChart.Data slices.",
            """
            <PieChart fx:id="distribution" title="Distribution"/>
            """.trimIndent(),
            controllerExample = """
            @FXML private PieChart distribution;

            @FXML
            public void initialize() {
                distribution.setData(FXCollections.observableArrayList(
                    new PieChart.Data("iOS",     38),
                    new PieChart.Data("Android", 54),
                    new PieChart.Data("Other",    8)
                ));
            }
            """.trimIndent(),
            javadocUrl = "$CHART/PieChart.html",
        ),

    ).associateBy { it.tagName }

    fun find(tagName: String): ComponentDoc? = entries[tagName]

    /**
     * Always return a doc — falls back to a generated stub for widgets without
     * a curated entry, so the help window is never empty.
     */
    fun get(tagName: String, importFqn: String): ComponentDoc =
        entries[tagName] ?: stubFor(tagName, importFqn)

    private fun stubFor(tagName: String, importFqn: String): ComponentDoc {
        val pkg = importFqn.substringBeforeLast('.', "")
        val javadoc = if (pkg.startsWith("javafx")) {
            val module = pkg.split('.').take(2).joinToString(".")
            "$JFX/$module/${pkg.replace('.', '/')}/$tagName.html"
        } else ""
        return ComponentDoc(
            tagName = tagName,
            summary = "$tagName — no curated docs yet. See the Oracle Javadoc for the full API.",
            fxmlExample = "<$tagName fx:id=\"my$tagName\"/>",
            javadocUrl = javadoc,
        )
    }
}
