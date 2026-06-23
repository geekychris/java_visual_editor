package com.visualjava.alignment

import com.visualjava.designer.DesignCanvasPanel.GeometryUpdate
import com.visualjava.preview.PreviewClient

/**
 * Pure functions that compute new geometry for a multi-selection given
 * one of the standard VB6 align/distribute actions.
 *
 * Caller is responsible for committing the resulting list via the
 * GeometryEditHandler so it lands as a single undo step.
 */
object AlignActions {

    enum class Kind {
        ALIGN_LEFT, ALIGN_CENTER_X, ALIGN_RIGHT,
        ALIGN_TOP, ALIGN_MIDDLE_Y, ALIGN_BOTTOM,
        DISTRIBUTE_H, DISTRIBUTE_V,
    }

    fun apply(
        kind: Kind,
        nodes: List<PreviewClient.NodeBounds>,
        formWidth: Int? = null,
        formHeight: Int? = null,
    ): List<GeometryUpdate> {
        if (nodes.isEmpty()) return emptyList()
        if (nodes.size == 1) {
            if (formWidth == null || formHeight == null) return emptyList()
            return applyToFormBounds(kind, nodes[0], formWidth, formHeight)
        }
        return when (kind) {
            Kind.ALIGN_LEFT -> alignTo(nodes) { _ -> nodes.minOf { it.x }.toInt() }
                .map { (n, x) -> GeometryUpdate(n.fxId, x, n.y.toInt(), n.w.toInt(), n.h.toInt()) }
            Kind.ALIGN_CENTER_X -> {
                val cx = nodes.map { (it.x + it.w / 2).toInt() }.average().toInt()
                nodes.map { GeometryUpdate(it.fxId, cx - (it.w / 2).toInt(), it.y.toInt(), it.w.toInt(), it.h.toInt()) }
            }
            Kind.ALIGN_RIGHT -> {
                val r = nodes.maxOf { (it.x + it.w).toInt() }
                nodes.map { GeometryUpdate(it.fxId, r - it.w.toInt(), it.y.toInt(), it.w.toInt(), it.h.toInt()) }
            }
            Kind.ALIGN_TOP -> {
                val t = nodes.minOf { it.y }.toInt()
                nodes.map { GeometryUpdate(it.fxId, it.x.toInt(), t, it.w.toInt(), it.h.toInt()) }
            }
            Kind.ALIGN_MIDDLE_Y -> {
                val cy = nodes.map { (it.y + it.h / 2).toInt() }.average().toInt()
                nodes.map { GeometryUpdate(it.fxId, it.x.toInt(), cy - (it.h / 2).toInt(), it.w.toInt(), it.h.toInt()) }
            }
            Kind.ALIGN_BOTTOM -> {
                val b = nodes.maxOf { (it.y + it.h).toInt() }
                nodes.map { GeometryUpdate(it.fxId, it.x.toInt(), b - it.h.toInt(), it.w.toInt(), it.h.toInt()) }
            }
            Kind.DISTRIBUTE_H -> distribute(nodes, horizontal = true)
            Kind.DISTRIBUTE_V -> distribute(nodes, horizontal = false)
        }
    }

    private fun alignTo(nodes: List<PreviewClient.NodeBounds>, target: (PreviewClient.NodeBounds) -> Int)
            : List<Pair<PreviewClient.NodeBounds, Int>> =
        nodes.map { it to target(it) }

    private fun applyToFormBounds(
        kind: Kind,
        n: PreviewClient.NodeBounds,
        formW: Int,
        formH: Int,
    ): List<GeometryUpdate> {
        val w = n.w.toInt(); val h = n.h.toInt()
        return when (kind) {
            Kind.ALIGN_LEFT -> listOf(GeometryUpdate(n.fxId, 0, n.y.toInt(), w, h))
            Kind.ALIGN_CENTER_X -> listOf(GeometryUpdate(n.fxId, (formW - w) / 2, n.y.toInt(), w, h))
            Kind.ALIGN_RIGHT -> listOf(GeometryUpdate(n.fxId, formW - w, n.y.toInt(), w, h))
            Kind.ALIGN_TOP -> listOf(GeometryUpdate(n.fxId, n.x.toInt(), 0, w, h))
            Kind.ALIGN_MIDDLE_Y -> listOf(GeometryUpdate(n.fxId, n.x.toInt(), (formH - h) / 2, w, h))
            Kind.ALIGN_BOTTOM -> listOf(GeometryUpdate(n.fxId, n.x.toInt(), formH - h, w, h))
            Kind.DISTRIBUTE_H, Kind.DISTRIBUTE_V -> emptyList()
        }
    }

    /**
     * Even out the spacing between centers from leftmost to rightmost
     * (or top to bottom). The two extreme components stay put.
     */
    private fun distribute(nodes: List<PreviewClient.NodeBounds>, horizontal: Boolean): List<GeometryUpdate> {
        val sorted = if (horizontal) nodes.sortedBy { it.x + it.w / 2 } else nodes.sortedBy { it.y + it.h / 2 }
        if (sorted.size < 3) return emptyList()
        val first = sorted.first(); val last = sorted.last()
        val (startCenter, endCenter) = if (horizontal) {
            (first.x + first.w / 2) to (last.x + last.w / 2)
        } else {
            (first.y + first.h / 2) to (last.y + last.h / 2)
        }
        val gap = (endCenter - startCenter) / (sorted.size - 1)
        return sorted.mapIndexed { i, n ->
            val centerTarget = (startCenter + gap * i).toInt()
            if (horizontal) {
                val newX = centerTarget - (n.w / 2).toInt()
                GeometryUpdate(n.fxId, newX, n.y.toInt(), n.w.toInt(), n.h.toInt())
            } else {
                val newY = centerTarget - (n.h / 2).toInt()
                GeometryUpdate(n.fxId, n.x.toInt(), newY, n.w.toInt(), n.h.toInt())
            }
        }
    }
}
