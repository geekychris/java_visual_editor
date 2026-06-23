package com.visualjava.wizard

/**
 * Curated FXML templates available from File → New → FXML Form.
 *
 * Each template provides a [name] shown in the wizard, a one-line
 * [description], and the literal FXML body to write. They are intentionally
 * lean — pre-wired structure but no recipes applied — so users can immediately
 * tweak in the designer.
 */
enum class FormTemplate(
    val displayName: String,
    val description: String,
    val fxml: String,
) {
    BLANK(
        "Blank Form",
        "Empty AnchorPane.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="600" prefHeight="400">
        |    <children>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    LOGIN(
        "Login Form",
        "Username + Password + Login/Cancel.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.PasswordField?>
        |<?import javafx.scene.control.TextField?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="380" prefHeight="220">
        |    <children>
        |        <Label fx:id="title" text="Sign In" layoutX="24" layoutY="20"
        |               style="-fx-font-size: 20px; -fx-font-weight: bold;"/>
        |
        |        <Label text="Username" layoutX="24" layoutY="70"/>
        |        <TextField fx:id="usernameField" layoutX="110" layoutY="66" prefWidth="240"
        |                   promptText="username"/>
        |
        |        <Label text="Password" layoutX="24" layoutY="106"/>
        |        <PasswordField fx:id="passwordField" layoutX="110" layoutY="102" prefWidth="240"
        |                       promptText="password"/>
        |
        |        <Button fx:id="loginBtn" text="Sign In" layoutX="220" layoutY="160"
        |                prefWidth="130" defaultButton="true"/>
        |        <Button fx:id="cancelBtn" text="Cancel" layoutX="110" layoutY="160"
        |                prefWidth="100" cancelButton="true"/>
        |
        |        <Label fx:id="errorLabel" text="" layoutX="24" layoutY="190"
        |               style="-fx-text-fill: #c0392b;"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    ABOUT(
        "About Box",
        "Icon + app name + version + OK.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.image.ImageView?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="400" prefHeight="240">
        |    <children>
        |        <ImageView fx:id="appIcon" layoutX="24" layoutY="24"
        |                   fitWidth="64" fitHeight="64" preserveRatio="true"/>
        |        <Label fx:id="appName" text="Application Name" layoutX="110" layoutY="32"
        |               style="-fx-font-size: 18px; -fx-font-weight: bold;"/>
        |        <Label fx:id="appVersion" text="Version 1.0.0" layoutX="110" layoutY="62"
        |               style="-fx-text-fill: #777;"/>
        |        <Label fx:id="appDescription" text="A short description." layoutX="24" layoutY="110"
        |               prefWidth="350" wrapText="true"/>
        |        <Label fx:id="appCopyright" text="© Your Company" layoutX="24" layoutY="180"
        |               style="-fx-text-fill: #999;"/>
        |        <Button fx:id="okBtn" text="OK" layoutX="300" layoutY="195"
        |                prefWidth="80" defaultButton="true"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    SPLASH(
        "Splash Screen",
        "Logo + tagline + progress while the app starts.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.ProgressBar?>
        |<?import javafx.scene.image.ImageView?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="500" prefHeight="280"
        |            style="-fx-background-color: linear-gradient(to bottom right, #232526, #414345);">
        |    <children>
        |        <ImageView fx:id="logo" layoutX="210" layoutY="50"
        |                   fitWidth="80" fitHeight="80" preserveRatio="true"/>
        |        <Label fx:id="appName" text="Application Name" layoutX="100" layoutY="150"
        |               prefWidth="300" alignment="CENTER"
        |               style="-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;"/>
        |        <Label fx:id="tagline" text="Loading…" layoutX="100" layoutY="180"
        |               prefWidth="300" alignment="CENTER"
        |               style="-fx-text-fill: #ccc;"/>
        |        <ProgressBar fx:id="progress" layoutX="100" layoutY="220"
        |                     prefWidth="300" progress="-1"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    SETTINGS(
        "Settings Dialog",
        "Sections + checkboxes + Apply/Cancel.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.CheckBox?>
        |<?import javafx.scene.control.ChoiceBox?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.Separator?>
        |<?import javafx.scene.control.Slider?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="440" prefHeight="380">
        |    <children>
        |        <Label text="Settings" layoutX="20" layoutY="16"
        |               style="-fx-font-size: 18px; -fx-font-weight: bold;"/>
        |        <Separator layoutX="20" layoutY="46" prefWidth="400"/>
        |
        |        <Label text="General" layoutX="20" layoutY="60"
        |               style="-fx-font-weight: bold;"/>
        |        <CheckBox fx:id="launchAtStartup" text="Launch at startup" layoutX="20" layoutY="86"/>
        |        <CheckBox fx:id="checkForUpdates" text="Check for updates" layoutX="20" layoutY="110"/>
        |
        |        <Label text="Appearance" layoutX="20" layoutY="146"
        |               style="-fx-font-weight: bold;"/>
        |        <Label text="Theme" layoutX="20" layoutY="172"/>
        |        <ChoiceBox fx:id="themeChoice" layoutX="120" layoutY="168" prefWidth="200"/>
        |
        |        <Label text="UI Scale" layoutX="20" layoutY="202"/>
        |        <Slider fx:id="uiScale" layoutX="120" layoutY="200" prefWidth="200"
        |                min="0.75" max="1.5" value="1.0" majorTickUnit="0.25"
        |                showTickLabels="true" snapToTicks="true"/>
        |
        |        <Separator layoutX="20" layoutY="290" prefWidth="400"/>
        |        <Button fx:id="cancelBtn" text="Cancel" layoutX="220" layoutY="320"
        |                prefWidth="90" cancelButton="true"/>
        |        <Button fx:id="applyBtn" text="Apply" layoutX="320" layoutY="320"
        |                prefWidth="100" defaultButton="true"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    WIZARD(
        "Wizard (3 steps)",
        "Header + step indicator + Back/Next/Finish.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.Separator?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="560" prefHeight="380">
        |    <children>
        |        <Label fx:id="wizardTitle" text="Wizard" layoutX="20" layoutY="20"
        |               style="-fx-font-size: 18px; -fx-font-weight: bold;"/>
        |        <Label fx:id="stepLabel" text="Step 1 of 3" layoutX="20" layoutY="50"
        |               style="-fx-text-fill: #888;"/>
        |        <Separator layoutX="20" layoutY="78" prefWidth="520"/>
        |
        |        <AnchorPane fx:id="stepContent" layoutX="20" layoutY="92"
        |                    prefWidth="520" prefHeight="220"/>
        |
        |        <Separator layoutX="20" layoutY="320" prefWidth="520"/>
        |        <Button fx:id="backBtn" text="Back" layoutX="290" layoutY="340"
        |                prefWidth="80" disable="true"/>
        |        <Button fx:id="nextBtn" text="Next" layoutX="380" layoutY="340"
        |                prefWidth="80" defaultButton="true"/>
        |        <Button fx:id="finishBtn" text="Finish" layoutX="470" layoutY="340"
        |                prefWidth="80" visible="false"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    CRUD(
        "CRUD Form",
        "List on the left, detail form on the right, Add/Save/Delete.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.ListView?>
        |<?import javafx.scene.control.Separator?>
        |<?import javafx.scene.control.TextField?>
        |<?import javafx.scene.layout.AnchorPane?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="640" prefHeight="420">
        |    <children>
        |        <Label text="Records" layoutX="16" layoutY="12"
        |               style="-fx-font-weight: bold;"/>
        |        <ListView fx:id="recordList" layoutX="16" layoutY="38"
        |                  prefWidth="220" prefHeight="320"/>
        |        <Button fx:id="addBtn" text="Add" layoutX="16" layoutY="368" prefWidth="100"/>
        |        <Button fx:id="deleteBtn" text="Delete" layoutX="136" layoutY="368" prefWidth="100"/>
        |
        |        <Separator orientation="VERTICAL" layoutX="256" layoutY="38" prefHeight="350"/>
        |
        |        <Label text="Detail" layoutX="280" layoutY="12"
        |               style="-fx-font-weight: bold;"/>
        |        <Label text="Name" layoutX="280" layoutY="48"/>
        |        <TextField fx:id="nameField" layoutX="360" layoutY="44" prefWidth="260"/>
        |        <Label text="Email" layoutX="280" layoutY="84"/>
        |        <TextField fx:id="emailField" layoutX="360" layoutY="80" prefWidth="260"/>
        |        <Label text="Phone" layoutX="280" layoutY="120"/>
        |        <TextField fx:id="phoneField" layoutX="360" layoutY="116" prefWidth="260"/>
        |        <Label text="Notes" layoutX="280" layoutY="156"/>
        |        <TextField fx:id="notesField" layoutX="360" layoutY="152" prefWidth="260"/>
        |
        |        <Button fx:id="saveBtn" text="Save" layoutX="520" layoutY="368"
        |                prefWidth="100" defaultButton="true"/>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    ),

    WITH_CHROME(
        "Form with Menu + Toolbar + Status",
        "MenuBar at the top, ToolBar below it, content area, status label.",
        """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<?import javafx.scene.control.Button?>
        |<?import javafx.scene.control.Label?>
        |<?import javafx.scene.control.Menu?>
        |<?import javafx.scene.control.MenuBar?>
        |<?import javafx.scene.control.MenuItem?>
        |<?import javafx.scene.control.Separator?>
        |<?import javafx.scene.control.ToolBar?>
        |<?import javafx.scene.layout.AnchorPane?>
        |<?import javafx.scene.layout.HBox?>
        |
        |<AnchorPane xmlns="http://javafx.com/javafx" xmlns:fx="http://javafx.com/fxml"
        |            prefWidth="700" prefHeight="500">
        |    <children>
        |        <MenuBar fx:id="menuBar" layoutX="0" layoutY="0" prefWidth="700"
        |                 AnchorPane.leftAnchor="0" AnchorPane.rightAnchor="0"
        |                 AnchorPane.topAnchor="0">
        |            <menus>
        |                <Menu text="File">
        |                    <items>
        |                        <MenuItem fx:id="newMenuItem"  text="New"     accelerator="Shortcut+N"/>
        |                        <MenuItem fx:id="openMenuItem" text="Open…"   accelerator="Shortcut+O"/>
        |                        <MenuItem fx:id="saveMenuItem" text="Save"    accelerator="Shortcut+S"/>
        |                        <MenuItem fx:id="exitMenuItem" text="Exit"    accelerator="Shortcut+Q"/>
        |                    </items>
        |                </Menu>
        |                <Menu text="Edit">
        |                    <items>
        |                        <MenuItem fx:id="undoMenuItem" text="Undo" accelerator="Shortcut+Z"/>
        |                        <MenuItem fx:id="redoMenuItem" text="Redo" accelerator="Shortcut+Shift+Z"/>
        |                    </items>
        |                </Menu>
        |                <Menu text="Help">
        |                    <items>
        |                        <MenuItem fx:id="aboutMenuItem" text="About…"/>
        |                    </items>
        |                </Menu>
        |            </menus>
        |        </MenuBar>
        |
        |        <ToolBar fx:id="toolBar" layoutX="0" layoutY="28" prefWidth="700"
        |                 AnchorPane.leftAnchor="0" AnchorPane.rightAnchor="0">
        |            <items>
        |                <Button fx:id="newBtn" text="New"/>
        |                <Button fx:id="openBtn" text="Open"/>
        |                <Button fx:id="saveBtn" text="Save"/>
        |                <Separator orientation="VERTICAL"/>
        |                <Button fx:id="refreshBtn" text="Refresh"/>
        |            </items>
        |        </ToolBar>
        |
        |        <AnchorPane fx:id="contentArea" layoutX="0" layoutY="70"
        |                    AnchorPane.leftAnchor="0" AnchorPane.rightAnchor="0"
        |                    AnchorPane.topAnchor="70" AnchorPane.bottomAnchor="28">
        |            <children>
        |            </children>
        |        </AnchorPane>
        |
        |        <HBox fx:id="statusBar" layoutY="472" prefWidth="700" prefHeight="28"
        |              style="-fx-background-color: #eaeaea; -fx-border-color: #c0c0c0;
        |                     -fx-border-width: 1 0 0 0; -fx-padding: 4 8;"
        |              AnchorPane.leftAnchor="0" AnchorPane.rightAnchor="0"
        |              AnchorPane.bottomAnchor="0">
        |            <children>
        |                <Label fx:id="statusLabel" text="Ready"/>
        |            </children>
        |        </HBox>
        |    </children>
        |</AnchorPane>
        """.trimMargin() + "\n",
    );
}
