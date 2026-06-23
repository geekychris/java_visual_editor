package com.visualjava.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-scoped bus that ties the designer canvas to the inspector tool window.
 *
 * The canvas writes (activeFile, selectedFxId) here whenever the user clicks a
 * component or switches editor tabs. The inspector tool window subscribes and
 * refreshes its property table.
 *
 * Both ends run on the EDT; no locking needed.
 */
@Service(Service.Level.PROJECT)
class DesignerSessionService(private val project: Project) : Disposable {

    data class Selection(val file: VirtualFile?, val fxId: String?)

    @Volatile var selection: Selection = Selection(null, null)
        private set

    private val listeners = mutableListOf<(Selection) -> Unit>()

    fun set(file: VirtualFile?, fxId: String?) {
        val next = Selection(file, fxId)
        if (next == selection) return
        selection = next
        listeners.forEach { it(next) }
    }

    fun addChangeListener(listener: (Selection) -> Unit): Disposable {
        listeners += listener
        return Disposable { listeners -= listener }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): DesignerSessionService =
            project.getService(DesignerSessionService::class.java)
    }
}
