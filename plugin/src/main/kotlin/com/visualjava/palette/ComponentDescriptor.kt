package com.visualjava.palette

/**
 * One palette entry.
 *
 * [tagName] is the FXML tag (`Button`, `Label`, ...).
 * [displayName] is shown in the palette list.
 * [importFqn] is added to the FXML's `<?import ... ?>` declarations if missing.
 * [category] groups the entry under a section header in the palette.
 * [defaultAttrs] are seeded onto the new element on drop (in addition to fx:id,
 * layoutX, layoutY which the drop handler always sets).
 */
data class ComponentDescriptor(
    val tagName: String,
    val displayName: String,
    val importFqn: String,
    val category: Category,
    val defaultAttrs: Map<String, String> = emptyMap(),
    /**
     * Optional body XML inserted between the open/close tags. Needed for
     * widgets that the FXMLLoader can't construct empty — e.g. charts need
     * `<xAxis>`/`<yAxis>`. When non-null, the new element is written as
     * `<Tag attrs>bodyXml</Tag>` instead of `<Tag attrs/>`.
     */
    val bodyXml: String? = null,
    /** Extra `<?import ... ?>` declarations needed by [bodyXml]. */
    val extraImports: List<String> = emptyList(),
) {
    enum class Category(val title: String) {
        CONTAINERS("Containers"),
        CONTROLS("Controls"),
        COLLECTIONS("Lists & Tables"),
        DISPLAY("Display"),
    }
}
