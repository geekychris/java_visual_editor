package com.visualjava.help

/**
 * One reference card for a palette widget. Drives both the palette hover
 * tooltip and the Help tool window.
 *
 * [fxmlExample] is the recommended FXML snippet for the widget — copy-pastable,
 * fx:id-bearing, with the attributes a real user usually wants.
 * [commonProperties] are the editable-via-inspector ones, with a one-line use.
 * [commonEvents] are the wire-up handlers (onAction, onMouseClicked, …).
 * [javadocUrl] points at the canonical Oracle JavaFX 21 page for the class.
 *
 * Entries are hand-curated; widgets with no entry fall back to a generated
 * "summary + Oracle link" stub so nothing is unannotated.
 */
data class ComponentDoc(
    val tagName: String,
    val summary: String,
    val fxmlExample: String,
    /** Java controller snippet: @FXML field + typical usage code. */
    val controllerExample: String? = null,
    val commonProperties: List<Pair<String, String>> = emptyList(),
    val commonEvents: List<Pair<String, String>> = emptyList(),
    val javadocUrl: String,
    val tutorial: String? = null,
)
