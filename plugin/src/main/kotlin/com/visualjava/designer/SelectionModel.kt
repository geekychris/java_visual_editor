package com.visualjava.designer

import com.visualjava.preview.PreviewClient

/**
 * Multi-select aware. The first selection is the "primary" — handles render
 * on it, the Properties panel shows its attributes. Additional nodes are
 * extras: used by align actions, painted with a thinner outline.
 */
class SelectionModel {

    @Volatile var selected: PreviewClient.NodeBounds? = null
        private set
    @Volatile var additional: List<PreviewClient.NodeBounds> = emptyList()
        private set

    /** All currently selected nodes (primary first). */
    fun all(): List<PreviewClient.NodeBounds> =
        selected?.let { listOf(it) + additional } ?: emptyList()

    private val listeners = mutableListOf<() -> Unit>()

    /** Replace selection with a single node (or clear). */
    fun select(node: PreviewClient.NodeBounds?) {
        if (node == selected && additional.isEmpty()) return
        selected = node
        additional = emptyList()
        fire()
    }

    /** Add to or remove from the selection (ctrl/cmd-click semantics). */
    fun toggle(node: PreviewClient.NodeBounds) {
        val all = all()
        if (all.any { it.fxId == node.fxId }) {
            // Remove
            val remaining = all.filter { it.fxId != node.fxId }
            selected = remaining.firstOrNull()
            additional = if (remaining.size > 1) remaining.drop(1) else emptyList()
        } else {
            if (selected == null) {
                selected = node
            } else {
                additional = additional + node
            }
        }
        fire()
    }

    /** Bulk replace from a fresh frame: refresh known nodes, drop missing ones. */
    fun rebind(nodesByFxId: Map<String, PreviewClient.NodeBounds>) {
        val newPrimary = selected?.let { nodesByFxId[it.fxId] }
        val newAdditional = additional.mapNotNull { nodesByFxId[it.fxId] }
        if (newPrimary == selected && newAdditional == additional) return
        selected = newPrimary ?: newAdditional.firstOrNull()
        additional = if (newPrimary != null) newAdditional else newAdditional.drop(1)
        fire()
    }

    fun clear() {
        if (selected == null && additional.isEmpty()) return
        selected = null
        additional = emptyList()
        fire()
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    private fun fire() {
        listeners.forEach { it() }
    }
}
