package com.visualjava.jpackage

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class JPackageAction : AnAction("Visual Java — Package App (jpackage)…"), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        JPackageDialog(project).show()
    }
}
