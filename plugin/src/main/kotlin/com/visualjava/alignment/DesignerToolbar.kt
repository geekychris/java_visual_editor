package com.visualjava.alignment

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JToggleButton

/** Slim toolbar above the design canvas with on/off toggles for alignment helpers. */
class DesignerToolbar(private val project: Project) : JBPanel<DesignerToolbar>(FlowLayout(FlowLayout.LEFT, 4, 2)) {

    private val settings = AlignmentSettings.getInstance(project)

    private val rulersBtn = toggle("Rulers", AllIcons.General.LayoutEditorOnly, settings.rulers) { settings.rulers = it }
    private val gridBtn = toggle("Grid", AllIcons.Graph.Grid, settings.grid) { settings.grid = it }
    private val snapBtn = toggle("Snap to grid", AllIcons.Actions.MoveTo2, settings.snapToGrid) { settings.snapToGrid = it }
    private val guidesBtn = toggle("Smart guides", AllIcons.Actions.PreviewDetails, settings.smartGuides) { settings.smartGuides = it }
    private val focusBtn = toggle("Highlight tab-focusable widgets", AllIcons.General.Locate, settings.highlightFocusable) { settings.highlightFocusable = it }

    private var wireUpButton: JButton? = null

    init {
        border = JBUI.Borders.empty(2, 6)
        add(JBLabel("View:").apply { border = JBUI.Borders.emptyRight(4) })
        add(rulersBtn); add(gridBtn); add(snapBtn); add(guidesBtn); add(focusBtn)
    }

    /** Install the "Wire-Up Recipe…" button. */
    fun installWireUpButton(onClick: () -> Unit) {
        add(JBLabel("  |  ").apply { border = JBUI.Borders.empty(0, 6) })
        val btn = JButton("Wire-Up…", AllIcons.Actions.IntentionBulb).apply {
            toolTipText = "Apply a wire-up recipe to selected components"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        }
        wireUpButton = btn
        add(btn)
    }

    /** Install the Tab Order dialog button. */
    fun installTabOrderButton(onClick: () -> Unit) {
        add(JButton("Tab Order…", AllIcons.Actions.ListChanges).apply {
            toolTipText = "Reorder focusable widgets"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        })
    }

    /** Install the POJO binding wizard button. */
    fun installPojoBindButton(onClick: () -> Unit) {
        add(JButton("Bind POJO…", AllIcons.Actions.IntentionBulbGrey).apply {
            toolTipText = "Bind controls to a Java class's properties"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        })
    }

    /** Install the "Wire All Unwired" button. */
    fun installBulkWireButton(onClick: () -> Unit) {
        add(JButton("Wire All", AllIcons.Actions.ListChanges).apply {
            toolTipText = "Wire default handlers for every widget that doesn't have one"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        })
    }

    /** Install the Menu Editor dialog button. */
    fun installMenuEditorButton(onClick: () -> Unit) {
        add(JButton("Menu…", AllIcons.Actions.GroupBy).apply {
            toolTipText = "Visual menu editor (MenuBar / Menu / MenuItem)"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        })
    }

    /** Install the "Run This Form" button. */
    fun installRunButton(onClick: () -> Unit) {
        val btn = JButton("Run", AllIcons.Actions.Execute).apply {
            toolTipText = "Run this form via Gradle :run"
            isFocusable = false
            border = JBUI.Borders.empty(2, 6)
            addActionListener { onClick() }
        }
        add(btn)
    }

    /**
     * Install a dropdown of preview sizes.
     *
     * The callback gets (width, height) in pixels each time the user picks an
     * entry. Form bounds (the first option) is reported as (0, 0) — the editor
     * interprets that as "use the FXML root's prefWidth/prefHeight".
     */
    fun installPreviewSizeDropdown(onPick: (Int, Int) -> Unit) {
        add(JBLabel("  Size:").apply { border = JBUI.Borders.empty(0, 6, 0, 4) })
        val combo = javax.swing.JComboBox(arrayOf(
            "Form bounds" to (0 to 0),
            "640 × 480" to (640 to 480),
            "800 × 600" to (800 to 600),
            "1024 × 768" to (1024 to 768),
            "1280 × 800" to (1280 to 800),
            "1366 × 768" to (1366 to 768),
            "1920 × 1080" to (1920 to 1080),
        )).apply {
            isFocusable = false
            toolTipText = "Render the design canvas at this size"
            renderer = object : javax.swing.DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
                ): java.awt.Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    @Suppress("UNCHECKED_CAST")
                    val pair = value as? Pair<String, *>
                    if (pair != null) text = pair.first
                    return c
                }
            }
            addActionListener {
                @Suppress("UNCHECKED_CAST")
                val pair = selectedItem as? Pair<String, Pair<Int, Int>> ?: return@addActionListener
                val (w, h) = pair.second
                onPick(w, h)
            }
        }
        add(combo)
    }

    /** Attach the align/distribute action buttons. */
    fun installAlignButtons(handlers: AlignActionHandlers) {
        add(JBLabel("  |  Align:").apply { border = JBUI.Borders.empty(0, 6, 0, 4) })
        add(actionButton("Align Left",       AllIcons.Actions.MoveToLeftTop)    { handlers.run(AlignActions.Kind.ALIGN_LEFT) })
        add(actionButton("Align Center",     AllIcons.Actions.PrettyPrint)      { handlers.run(AlignActions.Kind.ALIGN_CENTER_X) })
        add(actionButton("Align Right",      AllIcons.Actions.MoveToRightTop)   { handlers.run(AlignActions.Kind.ALIGN_RIGHT) })
        add(JBLabel(" ").apply { border = JBUI.Borders.empty(0, 2) })
        add(actionButton("Align Top",        AllIcons.Actions.MoveToTopLeft)    { handlers.run(AlignActions.Kind.ALIGN_TOP) })
        add(actionButton("Align Middle",     AllIcons.Actions.PrettyPrint)      { handlers.run(AlignActions.Kind.ALIGN_MIDDLE_Y) })
        add(actionButton("Align Bottom",     AllIcons.Actions.MoveToBottomLeft) { handlers.run(AlignActions.Kind.ALIGN_BOTTOM) })
        add(JBLabel(" ").apply { border = JBUI.Borders.empty(0, 2) })
        add(actionButton("Distribute Horizontally", AllIcons.Actions.MoveToButton) { handlers.run(AlignActions.Kind.DISTRIBUTE_H) })
        add(actionButton("Distribute Vertically",   AllIcons.Actions.MoveDown)     { handlers.run(AlignActions.Kind.DISTRIBUTE_V) })
    }

    private fun actionButton(tooltip: String, icon: javax.swing.Icon, onClick: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isFocusable = false
            border = JBUI.Borders.empty(2)
            addActionListener { onClick() }
        }
    }

    fun interface AlignActionHandlers {
        fun run(kind: AlignActions.Kind)
    }

    private fun toggle(tooltip: String, icon: javax.swing.Icon, initial: Boolean, onChange: (Boolean) -> Unit): JToggleButton {
        return JToggleButton(icon, initial).apply {
            toolTipText = tooltip
            isFocusable = false
            border = JBUI.Borders.empty(2)
            addActionListener { onChange(isSelected) }
        }
    }
}
