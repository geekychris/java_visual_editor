package com.visualjava.alignment

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Per-project, persistent on/off state for each alignment helper.
 *
 * All four can be toggled independently from the designer toolbar.
 */
@Service(Service.Level.PROJECT)
class AlignmentSettings(private val project: Project) : Disposable {

    private val props = PropertiesComponent.getInstance(project)

    var rulers: Boolean
        get() = props.getBoolean(KEY_RULERS, DEFAULT_RULERS)
        set(value) { props.setValue(KEY_RULERS, value, DEFAULT_RULERS); fire() }

    var grid: Boolean
        get() = props.getBoolean(KEY_GRID, DEFAULT_GRID)
        set(value) { props.setValue(KEY_GRID, value, DEFAULT_GRID); fire() }

    var snapToGrid: Boolean
        get() = props.getBoolean(KEY_SNAP_GRID, DEFAULT_SNAP_GRID)
        set(value) { props.setValue(KEY_SNAP_GRID, value, DEFAULT_SNAP_GRID); fire() }

    var smartGuides: Boolean
        get() = props.getBoolean(KEY_GUIDES, DEFAULT_GUIDES)
        set(value) { props.setValue(KEY_GUIDES, value, DEFAULT_GUIDES); fire() }

    /** Overlay focus-order badges on tab-focusable widgets in the canvas. */
    var highlightFocusable: Boolean
        get() = props.getBoolean(KEY_FOCUS, DEFAULT_FOCUS)
        set(value) { props.setValue(KEY_FOCUS, value, DEFAULT_FOCUS); fire() }

    val gridSize: Int get() = 16

    private val listeners = mutableListOf<() -> Unit>()
    fun addChangeListener(listener: () -> Unit): Disposable {
        listeners += listener
        return Disposable { listeners -= listener }
    }
    private fun fire() = listeners.forEach { it() }

    override fun dispose() { listeners.clear() }

    companion object {
        private const val KEY_RULERS = "visualjava.alignment.rulers"
        private const val KEY_GRID = "visualjava.alignment.grid"
        private const val KEY_SNAP_GRID = "visualjava.alignment.snapToGrid"
        private const val KEY_GUIDES = "visualjava.alignment.smartGuides"
        private const val KEY_FOCUS = "visualjava.alignment.highlightFocusable"
        private const val DEFAULT_RULERS = true
        private const val DEFAULT_GRID = false
        private const val DEFAULT_SNAP_GRID = false
        private const val DEFAULT_GUIDES = true
        private const val DEFAULT_FOCUS = false

        fun getInstance(project: Project): AlignmentSettings =
            project.getService(AlignmentSettings::class.java)
    }
}
