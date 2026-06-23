package com.visualjava.events

/**
 * Describes one JavaFX event property exposed in the FXML namespace.
 *
 * [property] is the FXML attribute name (e.g., "onAction").
 * [eventClassFqn] is the parameter type for the generated handler method.
 * [defaultMethodSuffix] is appended to the fxId to build the handler name:
 *   fxId="submitBtn" + suffix="Action" → submitBtnAction
 */
data class EventDescriptor(
    val property: String,
    val eventClassFqn: String,
    val defaultMethodSuffix: String,
) {
    fun methodName(fxId: String): String =
        if (fxId.isEmpty()) "on${defaultMethodSuffix}"
        else fxId + defaultMethodSuffix
}

/**
 * Per-FXML-tag event catalog. The first entry is the default (used by
 * double-click). The rest populate the right-click menu.
 *
 * Kept deliberately small for v1; richer per-widget events land in M4.5.
 */
object EventCatalog {

    private val ACTION = EventDescriptor("onAction", "javafx.event.ActionEvent", "Action")
    private val MOUSE_CLICKED = EventDescriptor("onMouseClicked", "javafx.scene.input.MouseEvent", "MouseClicked")
    private val MOUSE_PRESSED = EventDescriptor("onMousePressed", "javafx.scene.input.MouseEvent", "MousePressed")
    private val MOUSE_RELEASED = EventDescriptor("onMouseReleased", "javafx.scene.input.MouseEvent", "MouseReleased")
    private val MOUSE_ENTERED = EventDescriptor("onMouseEntered", "javafx.scene.input.MouseEvent", "MouseEntered")
    private val MOUSE_EXITED = EventDescriptor("onMouseExited", "javafx.scene.input.MouseEvent", "MouseExited")
    private val KEY_PRESSED = EventDescriptor("onKeyPressed", "javafx.scene.input.KeyEvent", "KeyPressed")
    private val KEY_RELEASED = EventDescriptor("onKeyReleased", "javafx.scene.input.KeyEvent", "KeyReleased")
    private val KEY_TYPED = EventDescriptor("onKeyTyped", "javafx.scene.input.KeyEvent", "KeyTyped")

    private val GENERIC_NODE_EVENTS = listOf(
        MOUSE_CLICKED, MOUSE_PRESSED, MOUSE_RELEASED,
        MOUSE_ENTERED, MOUSE_EXITED,
        KEY_PRESSED, KEY_RELEASED, KEY_TYPED,
    )

    /** Widgets where ActionEvent is the canonical "click/activate" event. */
    private val ACTION_TAGS = setOf(
        "Button", "ToggleButton", "CheckBox", "RadioButton",
        "ComboBox", "ChoiceBox", "MenuItem",
        "TextField", "PasswordField", // onAction = Enter pressed
    )

    fun defaultFor(tag: String): EventDescriptor =
        if (tag in ACTION_TAGS) ACTION else MOUSE_CLICKED

    fun allFor(tag: String): List<EventDescriptor> = buildList {
        if (tag in ACTION_TAGS) add(ACTION)
        addAll(GENERIC_NODE_EVENTS)
    }
}
