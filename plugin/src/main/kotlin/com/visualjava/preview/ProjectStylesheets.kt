package com.visualjava.preview

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Discover all .css files reachable from the project's resource roots, in a
 * stable order. The renderer applies them as Scene stylesheets so users see
 * their styles live in the preview.
 *
 * Read-action contracts: callers MUST wrap this in `ReadAction.compute { … }`
 * — both ModuleManager and ModuleRootManager require it.
 */
object ProjectStylesheets {

    fun discover(project: Project): List<String> {
        val out = linkedSetOf<String>()
        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).getSourceRoots(true)) {
                if (!root.path.contains("/resources")) continue
                walk(root, out)
            }
        }
        return out.toList()
    }

    private fun walk(dir: VirtualFile, out: MutableSet<String>) {
        for (child in dir.children) {
            if (child.isDirectory) {
                if (child.name.startsWith(".") || child.name == "build") continue
                walk(child, out)
            } else if (child.extension.equals("css", ignoreCase = true)) {
                // JavaFX Scene.getStylesheets accepts file: URLs.
                out += child.url
            }
        }
    }
}
