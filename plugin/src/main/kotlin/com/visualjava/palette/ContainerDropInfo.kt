package com.visualjava.palette

/**
 * How a given container accepts a dropped child.
 *
 * Most containers put children under `<children>`. Some need a different slot
 * (TabPane → `<tabs>`, ScrollPane → `<content>`, BorderPane → `<top>` /
 * `<center>` / etc.), and some need the child wrapped (TabPane wraps everything
 * in a `<Tab>`, Accordion in a `<TitledPane>`).
 */
data class ContainerDropInfo(
    /** XML element name of the slot, e.g. "children", "tabs", "content", "top". */
    val slot: String,
    /** true = slot is a collection (multiple children); false = singleton. */
    val collection: Boolean,
    /** If non-null, wrap the dropped node in this tag before inserting. */
    val wrapTagName: String? = null,
    /** FQN import added for the wrapper tag. */
    val wrapImport: String? = null,
    /** Attributes for the wrapper tag, in source order. */
    val wrapAttrs: Map<String, String> = emptyMap(),
)

object ContainerDropPolicy {
    /**
     * Decide where to drop into a container.
     *
     * @param tagName container's FXML tag (e.g. "BorderPane")
     * @param dropFrac (x, y) drop position normalised to container's bounds,
     *                 each in 0..1. Drives slot picking for BorderPane.
     * @return drop info, or null if this container doesn't accept children.
     */
    fun decide(tagName: String, dropFrac: Pair<Double, Double>): ContainerDropInfo? = when (tagName) {
        "Pane", "AnchorPane", "GridPane", "StackPane",
        "HBox", "VBox", "FlowPane", "TilePane" ->
            ContainerDropInfo("children", collection = true)

        "ScrollPane", "TitledPane" ->
            ContainerDropInfo("content", collection = false)

        "SplitPane", "ToolBar" ->
            ContainerDropInfo("items", collection = true)

        "TabPane" -> ContainerDropInfo(
            slot = "tabs",
            collection = true,
            wrapTagName = "Tab",
            wrapImport = "javafx.scene.control.Tab",
            wrapAttrs = mapOf("text" to "Tab"),
        )

        "Accordion" -> ContainerDropInfo(
            slot = "panes",
            collection = true,
            wrapTagName = "TitledPane",
            wrapImport = "javafx.scene.control.TitledPane",
            wrapAttrs = mapOf("text" to "Section"),
        )

        "BorderPane" -> {
            val (fx, fy) = dropFrac
            val slot = pickBorderPaneSlot(fx, fy)
            ContainerDropInfo(slot, collection = false)
        }

        else -> null
    }

    /**
     * BorderPane has 5 slots: top/bottom/left/right/center. Pick the one whose
     * region the drop lands in. The cross divides the pane in thirds; the
     * center occupies the middle third of both axes, the four edge regions
     * fill the remaining strips.
     */
    private fun pickBorderPaneSlot(fx: Double, fy: Double): String {
        val third = 1.0 / 3
        val inMiddleX = fx in third..(1 - third)
        val inMiddleY = fy in third..(1 - third)
        return when {
            inMiddleX && inMiddleY -> "center"
            !inMiddleX && inMiddleY -> if (fx < 0.5) "left" else "right"
            inMiddleX && !inMiddleY -> if (fy < 0.5) "top" else "bottom"
            // Corners — bias to the dominant axis distance
            else -> {
                val dxEdge = minOf(fx, 1 - fx)
                val dyEdge = minOf(fy, 1 - fy)
                if (dxEdge < dyEdge) {
                    if (fx < 0.5) "left" else "right"
                } else {
                    if (fy < 0.5) "top" else "bottom"
                }
            }
        }
    }
}
