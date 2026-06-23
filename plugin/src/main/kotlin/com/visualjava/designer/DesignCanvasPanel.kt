package com.visualjava.designer

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.visualjava.alignment.AlignmentSettings
import com.visualjava.palette.ComponentDescriptor
import com.visualjava.palette.ComponentTransferable
import com.visualjava.preview.PreviewClient
import com.visualjava.session.DesignerSessionService
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.TransferHandler

class DesignCanvasPanel(
    val project: Project,
    val selectionModel: SelectionModel = SelectionModel(),
) : JBPanel<DesignCanvasPanel>() {

    @Volatile private var frame: PreviewClient.Frame? = null
    @Volatile private var status: String? = "(no preview yet — switch to Design tab)"

    var onDoubleClick: ((PreviewClient.NodeBounds) -> Unit)? = null
    var onPopupRequested: ((PreviewClient.NodeBounds, MouseEvent) -> Unit)? = null
    var onComponentDrop: ((ComponentDescriptor, fxmlX: Double, fxmlY: Double, parentFxId: String?, dropInfo: com.visualjava.palette.ContainerDropInfo?) -> Unit)? = null
    var onDeleteRequested: ((List<String>) -> Unit)? = null

    private val containerTagNames = setOf(
        "Pane", "AnchorPane", "BorderPane", "GridPane", "StackPane",
        "HBox", "VBox", "FlowPane", "TilePane",
        // Non-Pane containers — they use specific slot names (tabs/items/
        // content/top/...) rather than <children>. See ContainerDropPolicy.
        "TabPane", "ScrollPane", "SplitPane", "TitledPane", "Accordion", "ToolBar",
    )

    /**
     * Drag finished. Single-node move/resize: just one entry. Multi-node drag:
     * one entry per moved node (translated by the same delta).
     */
    var onGeometryCommit: ((List<GeometryUpdate>) -> Unit)? = null

    data class GeometryUpdate(val fxId: String, val x: Int, val y: Int, val w: Int, val h: Int)

    private val settings = AlignmentSettings.getInstance(project)

    private enum class DragMode { NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SE, RESIZE_SW }
    private val handleSize = 8
    private val handleHitRadius = 8
    private val minSize = 12
    private val snapThreshold = 10
    private val rulerSize = 18
    private val guideHitRadius = 4

    private var dragMode: DragMode = DragMode.NONE
    private var dragStart: Point? = null
    private var primaryOriginal: PreviewClient.NodeBounds? = null
    private var primaryGhost: Rectangle? = null
    /** Original positions of additional selected nodes (multi-drag). */
    private var additionalOriginals: List<PreviewClient.NodeBounds> = emptyList()
    /** Active alignment guides to paint during drag, in FXML coordinates. */
    private data class Guide(val orientation: Orientation, val coord: Int)
    private enum class Orientation { VERTICAL, HORIZONTAL }
    private var activeGuides: List<Guide> = emptyList()

    /** User-placed ruler guides in FXML coords. Persisted via [DesignSidecar]. */
    private val verticalGuides = mutableListOf<Int>()
    private val horizontalGuides = mutableListOf<Int>()
    /** Pending guide being dragged out of a ruler. */
    private var rulerDragOut: Orientation? = null
    private var rulerDragOutCoord: Int? = null  // in FXML coords; null while outside canvas

    /**
     * The FXML file this canvas is editing. Set by the editor after construction
     * so the canvas can load/save the per-form .design.json sidecar (currently
     * just ruler guides). Null until set.
     */
    var fxmlFile: com.intellij.openapi.vfs.VirtualFile? = null
        set(value) {
            field = value
            if (value != null) {
                val data = DesignSidecar.load(value)
                verticalGuides.clear(); verticalGuides += data.verticalGuides
                horizontalGuides.clear(); horizontalGuides += data.horizontalGuides
                repaint()
            }
        }

    private fun saveSidecar() {
        val f = fxmlFile ?: return
        DesignSidecar.save(f, DesignSidecarData(verticalGuides.toList(), horizontalGuides.toList()))
    }

    init {
        background = JBColor(0xF7F7F7.toInt(), 0x2B2B2B.toInt())
        preferredSize = Dimension(800, 600)
        isFocusable = true

        selectionModel.addChangeListener { repaint() }
        settings.addChangeListener { repaint() }

        // External selection (e.g., Form Outline tree click) → reflect on canvas.
        DesignerSessionService.getInstance(project).addChangeListener { ext ->
            val sel = selectionModel.selected
            if (ext.fxId == sel?.fxId) return@addChangeListener
            val f = frame ?: return@addChangeListener
            val node = ext.fxId?.let { id -> f.nodes.firstOrNull { it.fxId == id } }
            if (node != null) selectionModel.select(node) else if (ext.fxId == null) selectionModel.clear()
        }

        transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean =
                support.isDataFlavorSupported(ComponentTransferable.FLAVOR)

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                val descriptor = support.transferable
                    .getTransferData(ComponentTransferable.FLAVOR) as? ComponentDescriptor ?: return false
                val drop: Point = support.dropLocation.dropPoint
                val f = frame
                val (ox, oy) = if (f != null) imageOffset(f) else (0 to 0)
                val absX = (drop.x - ox).coerceAtLeast(0)
                val absY = (drop.y - oy).coerceAtLeast(0)
                val target = containerAt(absX.toDouble(), absY.toDouble())
                val (relX, relY) = if (target != null) {
                    (absX - target.x.toInt()).coerceAtLeast(0) to (absY - target.y.toInt()).coerceAtLeast(0)
                } else {
                    absX to absY
                }
                val dropInfo = target?.let {
                    val fx = (relX.toDouble() / it.w.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                    val fy = (relY.toDouble() / it.h.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                    com.visualjava.palette.ContainerDropPolicy.decide(it.tagName, fx to fy)
                }
                onComponentDrop?.invoke(descriptor, relX.toDouble(), relY.toDouble(), target?.fxId, dropInfo)
                return true
            }
        }

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (e.isPopupTrigger) { handlePopup(e); return }

                // Drag-out from a ruler edge to drop a guide.
                if (settings.rulers) {
                    val r = rulerHit(e.x, e.y)
                    if (r != null) {
                        rulerDragOut = r
                        rulerDragOutCoord = null  // hidden until mouse moves into canvas
                        return
                    }
                }

                val sel = selectionModel.selected
                if (sel != null) {
                    val handle = handleHit(sel, e.x, e.y)
                    if (handle != null) { beginDrag(handle, e, sel); return }
                }
                val rawHit = hitTest(e.x, e.y)
                // Alt-click: walk up the parent chain from the topmost hit.
                val node = if (e.isAltDown && rawHit != null) {
                    parentOf(rawHit) ?: rawHit
                } else rawHit

                // Sticky drag: if the click lands inside the currently-selected
                // node's bounds, keep the selection and drag IT (don't switch to
                // whatever topmost child happens to be under the cursor). This
                // lets you grab a container — once selected — by clicking anywhere
                // inside, even if its children fully cover it.
                val clickInSelection = sel != null && fxmlPointInBounds(e.x, e.y, sel)
                if (clickInSelection && e.button == MouseEvent.BUTTON1 && !e.isAltDown
                    && !e.isMetaDown && !e.isControlDown
                ) {
                    beginDrag(DragMode.MOVE, e, sel!!)
                    return
                }

                if (node != null) {
                    val isMulti = e.isMetaDown || e.isControlDown
                    if (isMulti) selectionModel.toggle(node) else if (!selectionModel.all().any { it.fxId == node.fxId }) selectionModel.select(node)
                    if (e.button == MouseEvent.BUTTON1 && !isMulti && !e.isAltDown) {
                        val anchorNode = selectionModel.selected ?: node
                        beginDrag(DragMode.MOVE, e, anchorNode)
                    }
                } else {
                    selectionModel.clear()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                // Ruler drag-out: track current FXML coord (or null if outside canvas)
                if (rulerDragOut != null) {
                    rulerDragOutCoord = canvasToFxmlCoord(rulerDragOut!!, e.x, e.y)
                    repaint()
                    return
                }
                if (dragMode == DragMode.NONE) return
                val orig = primaryOriginal ?: return
                val start = dragStart ?: return
                val rawDx = e.x - start.x
                val rawDy = e.y - start.y
                val ghost = computeGhost(dragMode, orig, rawDx, rawDy)
                val (snapped, guides) = applySnapping(dragMode, ghost, orig)
                primaryGhost = snapped
                activeGuides = guides
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                // Ruler drag-out: commit guide if released inside the canvas area.
                if (rulerDragOut != null) {
                    val coord = rulerDragOutCoord
                    var committed = false
                    if (coord != null && coord >= 0) {
                        when (rulerDragOut) {
                            Orientation.VERTICAL -> { verticalGuides += coord; committed = true }
                            Orientation.HORIZONTAL -> { horizontalGuides += coord; committed = true }
                            null -> Unit
                        }
                    }
                    rulerDragOut = null
                    rulerDragOutCoord = null
                    if (committed) saveSidecar()
                    repaint()
                    return
                }
                if (e.isPopupTrigger) { handlePopup(e); resetDrag(); return }
                val ghost = primaryGhost
                val orig = primaryOriginal
                val sel = selectionModel.selected
                if (dragMode != DragMode.NONE && ghost != null && orig != null && sel != null) {
                    val changed = ghost.x != orig.x.toInt() || ghost.y != orig.y.toInt() ||
                        ghost.width != orig.w.toInt() || ghost.height != orig.h.toInt()
                    if (changed) {
                        val updates = mutableListOf(GeometryUpdate(sel.fxId, ghost.x, ghost.y, ghost.width, ghost.height))
                        if (dragMode == DragMode.MOVE) {
                            val dx = ghost.x - orig.x.toInt()
                            val dy = ghost.y - orig.y.toInt()
                            additionalOriginals.forEach { extra ->
                                updates += GeometryUpdate(extra.fxId,
                                    (extra.x.toInt() + dx).coerceAtLeast(0),
                                    (extra.y.toInt() + dy).coerceAtLeast(0),
                                    extra.w.toInt(), extra.h.toInt())
                            }
                        }
                        onGeometryCommit?.invoke(updates)
                    }
                }
                resetDrag()
                repaint()
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                    val node = selectionModel.selected ?: return
                    onDoubleClick?.invoke(node)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val sel = selectionModel.selected ?: run { cursor = Cursor.getDefaultCursor(); return }
                cursor = when (handleHit(sel, e.x, e.y)) {
                    DragMode.RESIZE_NW -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
                    DragMode.RESIZE_NE -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
                    DragMode.RESIZE_SE -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                    DragMode.RESIZE_SW -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)
                    else -> if (hitTest(e.x, e.y) != null) Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                            else Cursor.getDefaultCursor()
                }
            }

            private fun handlePopup(e: MouseEvent) {
                // Did the user right-click on a ruler guide?
                val guideHit = guideHit(e.x, e.y)
                if (guideHit != null) {
                    val menu = javax.swing.JPopupMenu()
                    val item = javax.swing.JMenuItem("Remove guide")
                    item.addActionListener {
                        when (guideHit.orientation) {
                            Orientation.VERTICAL -> verticalGuides.remove(guideHit.coord)
                            Orientation.HORIZONTAL -> horizontalGuides.remove(guideHit.coord)
                        }
                        saveSidecar()
                        repaint()
                    }
                    menu.add(item)
                    menu.show(this@DesignCanvasPanel, e.x, e.y)
                    return
                }
                val node = selectionModel.selected ?: hitTest(e.x, e.y)?.also { selectionModel.select(it) } ?: return
                onPopupRequested?.invoke(node, e)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)

        // Delete / Backspace removes the selection.
        val im = getInputMap(JComponent.WHEN_FOCUSED)
        val am = actionMap
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "vj-delete")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "vj-delete")
        am.put("vj-delete", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val ids = selectionModel.all().map { it.fxId }
                if (ids.isNotEmpty()) onDeleteRequested?.invoke(ids)
            }
        })

        // Arrow keys nudge selection by 1px; Shift+Arrow by grid size.
        fun bindNudge(keyCode: Int, mods: Int, dx: Int, dy: Int, name: String) {
            im.put(KeyStroke.getKeyStroke(keyCode, mods), name)
            am.put(name, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) = nudge(dx, dy)
            })
        }
        bindNudge(KeyEvent.VK_LEFT, 0, -1, 0, "vj-nudge-left")
        bindNudge(KeyEvent.VK_RIGHT, 0, 1, 0, "vj-nudge-right")
        bindNudge(KeyEvent.VK_UP, 0, 0, -1, "vj-nudge-up")
        bindNudge(KeyEvent.VK_DOWN, 0, 0, 1, "vj-nudge-down")
        val step = settings.gridSize
        bindNudge(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK, -step, 0, "vj-nudge-left-grid")
        bindNudge(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK, step, 0, "vj-nudge-right-grid")
        bindNudge(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK, 0, -step, "vj-nudge-up-grid")
        bindNudge(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK, 0, step, "vj-nudge-down-grid")
    }

    private fun nudge(dx: Int, dy: Int) {
        val sel = selectionModel.all()
        if (sel.isEmpty()) return
        val updates = sel.map { n ->
            GeometryUpdate(
                n.fxId,
                (n.x.toInt() + dx).coerceAtLeast(0),
                (n.y.toInt() + dy).coerceAtLeast(0),
                n.w.toInt(),
                n.h.toInt(),
            )
        }
        onGeometryCommit?.invoke(updates)
    }

    private fun beginDrag(mode: DragMode, e: MouseEvent, node: PreviewClient.NodeBounds) {
        dragMode = mode
        dragStart = Point(e.x, e.y)
        primaryOriginal = node
        primaryGhost = Rectangle(node.x.toInt(), node.y.toInt(), node.w.toInt(), node.h.toInt())
        additionalOriginals = selectionModel.additional
        activeGuides = emptyList()
    }

    private fun resetDrag() {
        dragMode = DragMode.NONE
        dragStart = null
        primaryOriginal = null
        primaryGhost = null
        additionalOriginals = emptyList()
        activeGuides = emptyList()
    }

    private fun computeGhost(mode: DragMode, orig: PreviewClient.NodeBounds, dx: Int, dy: Int): Rectangle {
        val ox = orig.x.toInt(); val oy = orig.y.toInt()
        val ow = orig.w.toInt(); val oh = orig.h.toInt()
        return when (mode) {
            DragMode.MOVE -> Rectangle((ox + dx).coerceAtLeast(0), (oy + dy).coerceAtLeast(0), ow, oh)
            DragMode.RESIZE_SE -> Rectangle(ox, oy, (ow + dx).coerceAtLeast(minSize), (oh + dy).coerceAtLeast(minSize))
            DragMode.RESIZE_SW -> {
                val newX = (ox + dx).coerceAtMost(ox + ow - minSize).coerceAtLeast(0)
                val newW = ox + ow - newX
                Rectangle(newX, oy, newW, (oh + dy).coerceAtLeast(minSize))
            }
            DragMode.RESIZE_NE -> {
                val newY = (oy + dy).coerceAtMost(oy + oh - minSize).coerceAtLeast(0)
                val newH = oy + oh - newY
                Rectangle(ox, newY, (ow + dx).coerceAtLeast(minSize), newH)
            }
            DragMode.RESIZE_NW -> {
                val newX = (ox + dx).coerceAtMost(ox + ow - minSize).coerceAtLeast(0)
                val newY = (oy + dy).coerceAtMost(oy + oh - minSize).coerceAtLeast(0)
                Rectangle(newX, newY, ox + ow - newX, oy + oh - newY)
            }
            DragMode.NONE -> Rectangle(ox, oy, ow, oh)
        }
    }

    /**
     * Apply snap-to-grid, smart-guide snapping, and ruler-guide snapping to the ghost.
     * Returns the snapped rectangle plus the guides to paint (smart-guide hints only;
     * ruler guides are always painted regardless of drag).
     *
     * Precedence: snap-to-grid is dominant when on (quantises to grid). When it's off,
     * smart guides apply. Ruler guides always apply on top — they're explicit user marks.
     */
    private fun applySnapping(
        mode: DragMode,
        ghost: Rectangle,
        orig: PreviewClient.NodeBounds,
    ): Pair<Rectangle, List<Guide>> {
        var x = ghost.x; var y = ghost.y; var w = ghost.width; var h = ghost.height
        val guides = mutableListOf<Guide>()

        // 1) Snap to grid (hard quantization). Smart guides apply on top if they
        //    find a closer alignment to an existing component edge.
        if (settings.snapToGrid) {
            val g = settings.gridSize
            fun snap(v: Int) = (v + g / 2) / g * g
            when (mode) {
                DragMode.MOVE -> { x = snap(x); y = snap(y) }
                DragMode.RESIZE_SE -> { w = snap(x + w) - x; h = snap(y + h) - y }
                DragMode.RESIZE_SW -> { val r = x + w; x = snap(x); w = r - x; h = snap(y + h) - y }
                DragMode.RESIZE_NE -> { val b = y + h; y = snap(y); h = b - y; w = snap(x + w) - x }
                DragMode.RESIZE_NW -> { val r = x + w; val b = y + h; x = snap(x); y = snap(y); w = r - x; h = b - y }
                DragMode.NONE -> Unit
            }
            w = w.coerceAtLeast(minSize); h = h.coerceAtLeast(minSize)
        }
        if (settings.smartGuides && frame != null) {
            val others = frame!!.nodes.filter { it.fxId != orig.fxId && selectionModel.additional.none { a -> a.fxId == it.fxId } }

            // For multi-drag, the secondary nodes move by the same (dx, dy) as
            // primary. Build the candidate edges from primary + every secondary
            // so a secondary edge can drive the snap too.
            val dx = x - orig.x.toInt(); val dy = y - orig.y.toInt()
            data class Edges(val left: Int, val right: Int, val cx: Int, val top: Int, val bottom: Int, val cy: Int)
            val myEdgesList = buildList {
                add(Edges(x, x + w, x + w / 2, y, y + h, y + h / 2))
                for (extra in additionalOriginals) {
                    val ex = extra.x.toInt() + dx
                    val ey = extra.y.toInt() + dy
                    val ew = extra.w.toInt(); val eh = extra.h.toInt()
                    add(Edges(ex, ex + ew, ex + ew / 2, ey, ey + eh, ey + eh / 2))
                }
            }

            var bestVx: Pair<Int, Int>? = null  // (snapEdgeOfMine, otherCoord)
            var bestVxDist = snapThreshold + 1
            var bestHy: Pair<Int, Int>? = null
            var bestHyDist = snapThreshold + 1

            for (other in others) {
                val oLeft = other.x.toInt(); val oRight = (other.x + other.w).toInt()
                val oCenterX = (other.x + other.w / 2).toInt()
                val oTop = other.y.toInt(); val oBottom = (other.y + other.h).toInt()
                val oCenterY = (other.y + other.h / 2).toInt()

                for (myEdges in myEdgesList) {
                    val myLeft = myEdges.left; val myRight = myEdges.right; val myCenterX = myEdges.cx
                    val myTop = myEdges.top; val myBottom = myEdges.bottom; val myCenterY = myEdges.cy

                    val vCandidates = listOf(
                        myLeft to oLeft, myLeft to oRight, myLeft to oCenterX,
                        myRight to oLeft, myRight to oRight, myRight to oCenterX,
                        myCenterX to oLeft, myCenterX to oRight, myCenterX to oCenterX,
                    )
                    for ((mine, target) in vCandidates) {
                        val d = kotlin.math.abs(mine - target)
                        if (d < bestVxDist) { bestVxDist = d; bestVx = mine to target }
                    }

                    val hCandidates = listOf(
                        myTop to oTop, myTop to oBottom, myTop to oCenterY,
                        myBottom to oTop, myBottom to oBottom, myBottom to oCenterY,
                        myCenterY to oTop, myCenterY to oBottom, myCenterY to oCenterY,
                    )
                    for ((mine, target) in hCandidates) {
                        val d = kotlin.math.abs(mine - target)
                        if (d < bestHyDist) { bestHyDist = d; bestHy = mine to target }
                    }
                }
            }
            // Re-bind primary-edge locals so the snap-application code below still works.
            val myLeft = x; val myRight = x + w; val myCenterX = x + w / 2
            val myTop = y; val myBottom = y + h; val myCenterY = y + h / 2

            bestVx?.let { (mine, target) ->
                val shift = target - mine
                when (mode) {
                    DragMode.MOVE -> x += shift
                    DragMode.RESIZE_SE, DragMode.RESIZE_NE -> if (mine == myRight) w += shift
                    DragMode.RESIZE_SW, DragMode.RESIZE_NW -> if (mine == myLeft) { x += shift; w -= shift }
                    else -> Unit
                }
                guides += Guide(Orientation.VERTICAL, target)
            }
            bestHy?.let { (mine, target) ->
                val shift = target - mine
                when (mode) {
                    DragMode.MOVE -> y += shift
                    DragMode.RESIZE_SE, DragMode.RESIZE_SW -> if (mine == myBottom) h += shift
                    DragMode.RESIZE_NE, DragMode.RESIZE_NW -> if (mine == myTop) { y += shift; h -= shift }
                    else -> Unit
                }
                guides += Guide(Orientation.HORIZONTAL, target)
            }
            w = w.coerceAtLeast(minSize); h = h.coerceAtLeast(minSize)
        }

        // 2) Ruler guides — always applied, on top of any of the above.
        if (verticalGuides.isNotEmpty()) {
            val edges = when (mode) {
                DragMode.MOVE -> listOf(x, x + w / 2, x + w)
                DragMode.RESIZE_SE, DragMode.RESIZE_NE -> listOf(x + w)
                DragMode.RESIZE_SW, DragMode.RESIZE_NW -> listOf(x)
                DragMode.NONE -> emptyList()
            }
            var bestShift: Int? = null; var bestEdge = 0; var bestTarget = 0
            var bestDist = snapThreshold + 1
            for (e2 in edges) for (vg in verticalGuides) {
                val d = kotlin.math.abs(vg - e2)
                if (d < bestDist) { bestDist = d; bestShift = vg - e2; bestEdge = e2; bestTarget = vg }
            }
            bestShift?.let { shift ->
                when (mode) {
                    DragMode.MOVE -> x += shift
                    DragMode.RESIZE_SE, DragMode.RESIZE_NE -> if (bestEdge == x + w) w += shift
                    DragMode.RESIZE_SW, DragMode.RESIZE_NW -> if (bestEdge == x) { x += shift; w -= shift }
                    else -> Unit
                }
            }
        }
        if (horizontalGuides.isNotEmpty()) {
            val edges = when (mode) {
                DragMode.MOVE -> listOf(y, y + h / 2, y + h)
                DragMode.RESIZE_SE, DragMode.RESIZE_SW -> listOf(y + h)
                DragMode.RESIZE_NE, DragMode.RESIZE_NW -> listOf(y)
                DragMode.NONE -> emptyList()
            }
            var bestShift: Int? = null; var bestEdge = 0
            var bestDist = snapThreshold + 1
            for (e2 in edges) for (hg in horizontalGuides) {
                val d = kotlin.math.abs(hg - e2)
                if (d < bestDist) { bestDist = d; bestShift = hg - e2; bestEdge = e2 }
            }
            bestShift?.let { shift ->
                when (mode) {
                    DragMode.MOVE -> y += shift
                    DragMode.RESIZE_SE, DragMode.RESIZE_SW -> if (bestEdge == y + h) h += shift
                    DragMode.RESIZE_NE, DragMode.RESIZE_NW -> if (bestEdge == y) { y += shift; h -= shift }
                    else -> Unit
                }
            }
        }
        w = w.coerceAtLeast(minSize); h = h.coerceAtLeast(minSize)

        return Rectangle(x.coerceAtLeast(0), y.coerceAtLeast(0), w, h) to guides
    }

    private fun rulerHit(canvasX: Int, canvasY: Int): Orientation? {
        // Top ruler (excluding the corner)
        if (canvasY < rulerSize && canvasX >= rulerSize) return Orientation.VERTICAL
        // Left ruler (excluding the corner)
        if (canvasX < rulerSize && canvasY >= rulerSize) return Orientation.HORIZONTAL
        return null
    }

    private fun canvasToFxmlCoord(orientation: Orientation, canvasX: Int, canvasY: Int): Int? {
        val f = frame ?: return null
        val (ox, oy) = imageOffset(f)
        return when (orientation) {
            Orientation.VERTICAL -> {
                val v = canvasX - ox
                if (v in 0..f.image.width) v else null
            }
            Orientation.HORIZONTAL -> {
                val v = canvasY - oy
                if (v in 0..f.image.height) v else null
            }
        }
    }

    private fun guideHit(canvasX: Int, canvasY: Int): Guide? {
        val f = frame ?: return null
        val (ox, oy) = imageOffset(f)
        verticalGuides.firstOrNull { kotlin.math.abs(canvasX - (ox + it)) <= guideHitRadius }
            ?.let { return Guide(Orientation.VERTICAL, it) }
        horizontalGuides.firstOrNull { kotlin.math.abs(canvasY - (oy + it)) <= guideHitRadius }
            ?.let { return Guide(Orientation.HORIZONTAL, it) }
        return null
    }

    private fun handleHit(sel: PreviewClient.NodeBounds, canvasX: Int, canvasY: Int): DragMode? {
        val f = frame ?: return null
        val (ox, oy) = imageOffset(f)
        val l = ox + sel.x.toInt(); val t = oy + sel.y.toInt()
        val r = l + sel.w.toInt(); val b = t + sel.h.toInt()
        fun near(px: Int, py: Int) =
            (canvasX - px) * (canvasX - px) + (canvasY - py) * (canvasY - py) <= handleHitRadius * handleHitRadius
        return when {
            near(l, t) -> DragMode.RESIZE_NW
            near(r, t) -> DragMode.RESIZE_NE
            near(r, b) -> DragMode.RESIZE_SE
            near(l, b) -> DragMode.RESIZE_SW
            else -> null
        }
    }

    fun currentFrameNodes(): List<PreviewClient.NodeBounds> = frame?.nodes.orEmpty()

    fun setFrame(newFrame: PreviewClient.Frame) {
        check(SwingUtilities.isEventDispatchThread()) { "setFrame must be called on EDT" }
        frame = newFrame
        status = null
        selectionModel.rebind(newFrame.nodes.associateBy { it.fxId })
        repaint()
    }

    fun setStatus(message: String) {
        check(SwingUtilities.isEventDispatchThread()) { "setStatus must be called on EDT" }
        status = message
        repaint()
    }

    private fun hitTest(canvasX: Int, canvasY: Int): PreviewClient.NodeBounds? {
        val f = frame ?: return null
        val (ox, oy) = imageOffset(f)
        val fx = (canvasX - ox).toDouble()
        val fy = (canvasY - oy).toDouble()
        return f.nodes.asReversed().firstOrNull { n ->
            fx in n.x..(n.x + n.w) && fy in n.y..(n.y + n.h)
        }
    }

    private fun fxmlPointInBounds(canvasX: Int, canvasY: Int, b: PreviewClient.NodeBounds): Boolean {
        val f = frame ?: return false
        val (ox, oy) = imageOffset(f)
        val fx = (canvasX - ox).toDouble()
        val fy = (canvasY - oy).toDouble()
        return fx in b.x..(b.x + b.w) && fy in b.y..(b.y + b.h)
    }

    /** The parent NodeBounds of [child], or null if it is at the root. */
    private fun parentOf(child: PreviewClient.NodeBounds): PreviewClient.NodeBounds? {
        val pid = child.parentFxId ?: return null
        val f = frame ?: return null
        return f.nodes.firstOrNull { it.fxId == pid }
    }

    /** Topmost container node whose bounds contain (fxmlX, fxmlY), or null. */
    private fun containerAt(fxmlX: Double, fxmlY: Double): PreviewClient.NodeBounds? {
        val f = frame ?: return null
        return f.nodes.asReversed().firstOrNull { n ->
            n.tagName in containerTagNames &&
                fxmlX in n.x..(n.x + n.w) && fxmlY in n.y..(n.y + n.h)
        }
    }

    private fun imageOffset(f: PreviewClient.Frame): Pair<Int, Int> {
        // Leave room for rulers at left/top when they're visible.
        val extraX = if (settings.rulers) rulerSize else 0
        val extraY = if (settings.rulers) rulerSize else 0
        val availW = width - extraX
        val availH = height - extraY
        val ox = extraX + ((availW - f.image.width) / 2).coerceAtLeast(0)
        val oy = extraY + ((availH - f.image.height) / 2).coerceAtLeast(0)
        return ox to oy
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val f = frame
        if (f != null) {
            val (ox, oy) = imageOffset(f)

            if (settings.grid) paintGrid(g2, ox, oy, f.image.width, f.image.height)

            g2.drawImage(f.image, ox, oy, null)

            // Selection chrome
            val primary = selectionModel.selected
            val extras = selectionModel.additional
            for (extra in extras) paintSelectionRect(g2, extra, ox, oy, color = JBColor(0xFF9F0A.toInt(), 0xFFB142.toInt()), thickness = 1.5f)
            if (primary != null) {
                paintSelectionRect(g2, primary, ox, oy, color = JBColor(0xFF3B30.toInt(), 0xFF453A.toInt()), thickness = 2f)
                val sx = ox + primary.x.toInt(); val sy = oy + primary.y.toInt()
                val sw = primary.w.toInt(); val sh = primary.h.toInt()
                drawHandle(g2, sx, sy); drawHandle(g2, sx + sw, sy)
                drawHandle(g2, sx + sw, sy + sh); drawHandle(g2, sx, sy + sh)
            }

            // User ruler guides (always visible)
            paintRulerGuides(g2, ox, oy, f.image.width, f.image.height)

            // Smart-guide hints (during drag)
            if (activeGuides.isNotEmpty()) paintGuides(g2, ox, oy, f.image.width, f.image.height)

            // Ghost (during drag)
            val ghost = primaryGhost
            if (ghost != null && dragMode != DragMode.NONE) {
                g2.color = JBColor(Color(0, 122, 255, 220), Color(0, 122, 255, 220))
                g2.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(6f, 4f), 0f)
                g2.drawRect(ox + ghost.x, oy + ghost.y, ghost.width, ghost.height)
            }

            // Ruler drag-out preview
            val dragOutCoord = rulerDragOutCoord
            val dragOutOrient = rulerDragOut
            if (dragOutOrient != null && dragOutCoord != null) {
                g2.color = JBColor(Color(0, 122, 255, 220), Color(0, 122, 255, 220))
                g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(5f, 3f), 0f)
                when (dragOutOrient) {
                    Orientation.VERTICAL -> g2.drawLine(ox + dragOutCoord, oy, ox + dragOutCoord, oy + f.image.height)
                    Orientation.HORIZONTAL -> g2.drawLine(ox, oy + dragOutCoord, ox + f.image.width, oy + dragOutCoord)
                }
            }

            if (settings.rulers) paintRulers(g2, ox, oy)
            if (settings.highlightFocusable) paintFocusBadges(g2, ox, oy, f)
            return
        }

        val msg = status ?: return
        g2.color = JBColor.GRAY
        val fm = g2.fontMetrics
        val tx = (width - fm.stringWidth(msg)) / 2
        val ty = height / 2
        g2.drawString(msg, tx, ty)
    }

    private fun paintSelectionRect(g2: Graphics2D, b: PreviewClient.NodeBounds, ox: Int, oy: Int, color: Color, thickness: Float) {
        g2.color = color
        g2.stroke = BasicStroke(thickness)
        g2.drawRect((ox + b.x).toInt() - 1, (oy + b.y).toInt() - 1, b.w.toInt() + 2, b.h.toInt() + 2)
    }

    private fun paintGrid(g2: Graphics2D, ox: Int, oy: Int, w: Int, h: Int) {
        val step = settings.gridSize
        g2.color = JBColor(Color(0, 0, 0, 28), Color(255, 255, 255, 28))
        var x = 0
        while (x <= w) { g2.fillRect(ox + x - 1, oy, 1, h); x += step * 4 }
        var y = 0
        while (y <= h) { g2.fillRect(ox, oy + y - 1, w, 1); y += step * 4 }
        // Dots at finer grid
        g2.color = JBColor(Color(0, 0, 0, 18), Color(255, 255, 255, 18))
        var gy = 0
        while (gy <= h) {
            var gx = 0
            while (gx <= w) { g2.fillRect(ox + gx, oy + gy, 1, 1); gx += step }
            gy += step
        }
    }

    private fun paintGuides(g2: Graphics2D, ox: Int, oy: Int, imgW: Int, imgH: Int) {
        g2.color = JBColor(Color(255, 105, 180, 220), Color(255, 105, 180, 220))
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 3f), 0f)
        for (gd in activeGuides) {
            when (gd.orientation) {
                Orientation.VERTICAL -> g2.drawLine(ox + gd.coord, oy, ox + gd.coord, oy + imgH)
                Orientation.HORIZONTAL -> g2.drawLine(ox, oy + gd.coord, ox + imgW, oy + gd.coord)
            }
        }
    }

    private fun paintRulerGuides(g2: Graphics2D, ox: Int, oy: Int, imgW: Int, imgH: Int) {
        g2.color = JBColor(Color(0, 122, 255, 220), Color(0, 122, 255, 220))
        g2.stroke = BasicStroke(1f)
        for (vg in verticalGuides) g2.drawLine(ox + vg, oy, ox + vg, oy + imgH)
        for (hg in horizontalGuides) g2.drawLine(ox, oy + hg, ox + imgW, oy + hg)
    }

    /**
     * Draw a small numbered orange badge on top-left of every tab-focusable
     * widget, in scene-graph order. Mirrors the Tab Order dialog's view but
     * inline on the canvas — a quick "is this what the user will tab through?"
     * a11y check without opening the dialog.
     */
    private fun paintFocusBadges(g2: Graphics2D, ox: Int, oy: Int, f: PreviewClient.Frame) {
        val focusables = f.nodes.filter { it.tagName in FOCUSABLE_TAGS }
        val orig = g2.color; val origStroke = g2.stroke
        g2.color = JBColor(Color(255, 130, 0, 220), Color(255, 165, 0, 220))
        g2.stroke = BasicStroke(1f)
        val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 10)
        g2.font = font
        val fm = g2.fontMetrics
        for ((i, n) in focusables.withIndex()) {
            val label = (i + 1).toString()
            val w = fm.stringWidth(label) + 6
            val h = fm.height
            val bx = ox + n.x.toInt() - 2
            val by = oy + n.y.toInt() - 2
            g2.fillRect(bx, by, w, h)
            g2.color = java.awt.Color.WHITE
            g2.drawString(label, bx + 3, by + fm.ascent - 1)
            g2.color = JBColor(Color(255, 130, 0, 220), Color(255, 165, 0, 220))
        }
        g2.color = orig; g2.stroke = origStroke
    }

    companion object {
        private val FOCUSABLE_TAGS = setOf(
            "Button", "ToggleButton", "MenuButton", "Hyperlink",
            "TextField", "PasswordField", "TextArea",
            "CheckBox", "RadioButton",
            "ComboBox", "ChoiceBox", "DatePicker", "ColorPicker", "Spinner", "Slider",
            "ListView", "TableView", "TreeView", "TreeTableView", "TabPane",
        )
    }

    private fun paintRulers(g2: Graphics2D, ox: Int, oy: Int) {
        val bg = JBColor(0xE6E6E6.toInt(), 0x363636.toInt())
        val tickC = JBColor(0x808080.toInt(), 0xA0A0A0.toInt())
        val labelC = JBColor(0x404040.toInt(), 0xCFCFCF.toInt())

        // Backgrounds
        g2.color = bg
        g2.fillRect(0, 0, width, rulerSize)
        g2.fillRect(0, 0, rulerSize, height)

        val f = frame ?: return
        val imgW = f.image.width
        val imgH = f.image.height

        g2.color = tickC
        g2.stroke = BasicStroke(1f)
        g2.font = g2.font.deriveFont(9f)

        // Top ruler: tick every 10px, label every 50px. fxmlX = 0 lives at ox.
        var x = 0
        while (x <= imgW) {
            val px = ox + x
            val tickH = if (x % 50 == 0) 8 else if (x % 10 == 0) 4 else 0
            if (tickH > 0) g2.drawLine(px, rulerSize - tickH, px, rulerSize)
            if (x % 50 == 0) {
                g2.color = labelC
                g2.drawString(x.toString(), px + 2, rulerSize - 10)
                g2.color = tickC
            }
            x += 10
        }

        // Left ruler
        var y = 0
        while (y <= imgH) {
            val py = oy + y
            val tickW = if (y % 50 == 0) 8 else if (y % 10 == 0) 4 else 0
            if (tickW > 0) g2.drawLine(rulerSize - tickW, py, rulerSize, py)
            if (y % 50 == 0) {
                g2.color = labelC
                g2.drawString(y.toString(), 2, py + 9)
                g2.color = tickC
            }
            y += 10
        }
    }

    private fun drawHandle(g2: Graphics2D, cx: Int, cy: Int) {
        val s = handleSize
        val x = cx - s / 2; val y = cy - s / 2
        val old = g2.stroke
        g2.color = JBColor.background()
        g2.fillRect(x, y, s, s)
        g2.color = JBColor(0xFF3B30.toInt(), 0xFF453A.toInt())
        g2.stroke = BasicStroke(1.5f)
        g2.drawRect(x, y, s, s)
        g2.stroke = old
    }
}
