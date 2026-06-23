package com.visualjava.run

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * "Run this form" — invokes the Gradle :run task in the module that owns the
 * currently-active FXML file. If no Gradle project is found, surfaces a
 * notification with instructions instead of failing silently.
 */
class RunCurrentFormAction(private val project: Project) {

    private val log = thisLogger()

    fun run(formFile: VirtualFile) {
        val gradleRoot = findGradleRoot(formFile)
        if (gradleRoot == null) {
            notify(
                "No Gradle project found",
                "Couldn't locate a build.gradle.kts above ${formFile.name}. " +
                    "The 'Run This Form' button needs a Gradle project (the JavaFX " +
                    "application plugin's `run` task).",
                NotificationType.WARNING,
            )
            return
        }
        runGradleTask(gradleRoot, "run")
    }

    private fun runGradleTask(gradleRoot: VirtualFile, taskName: String) {
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = gradleRoot.path
            taskNames = listOf(taskName)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        try {
            ExternalSystemUtil.runTask(
                settings,
                com.intellij.execution.executors.DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
            )
        } catch (e: Throwable) {
            log.warn("Failed to execute Gradle task $taskName", e)
            notify(
                "Run failed",
                "${e.javaClass.simpleName}: ${e.message ?: "(no message)"}",
                NotificationType.ERROR,
            )
        }
    }

    /** Walks up from [formFile] looking for a directory containing build.gradle[.kts]. */
    private fun findGradleRoot(formFile: VirtualFile): VirtualFile? {
        var cur: VirtualFile? = formFile.parent
        while (cur != null && VfsUtilCore.isAncestor(project.baseDir ?: cur, cur, false)) {
            if (cur.findChild("build.gradle.kts") != null || cur.findChild("build.gradle") != null) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }

    private fun notify(title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Visual Java")
            .createNotification(title, message, type)
            .notify(project)
    }
}
