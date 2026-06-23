package com.visualjava.inspector

/**
 * One row in the property inspector.
 *
 * [name] is the FXML attribute name (what gets written to the file).
 * [displayName] is what the user sees in the inspector table.
 * [kind] tells the editor what input control to show (v2 will branch on this;
 * v1 uses a string field for everything).
 */
data class PropertyDescriptor(
    val name: String,
    val displayName: String,
    val kind: Kind,
) {
    enum class Kind {
        STRING,
        NUMBER,
        BOOLEAN,
        /** Space-separated CSS class list, rendered as removable chips. */
        STYLE_CLASS,
        /** Image URL — rendered with a "Browse…" affordance that picks a file. */
        IMAGE,
    }
}
