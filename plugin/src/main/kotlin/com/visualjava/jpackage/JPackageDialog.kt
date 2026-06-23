package com.visualjava.jpackage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * jpackage wizard. Collects the metadata jpackage cares about (name, version,
 * main class, jar, vendor, icon), lets the user pick the platform package
 * type, and runs `jpackage` in a background process while streaming output
 * into the dialog's log area.
 *
 * v1 happy path: assumes the project already builds a runnable jar via Gradle
 * (Gradle's `application` plugin's shadowJar / fat-jar). The dialog defaults
 * `--input` to `<module>/build/libs` and `--main-jar` to the first jar there.
 */
class JPackageDialog(private val project: Project) : DialogWrapper(project, true) {

    private val appName = JBTextField("VisualJavaApp")
    private val appVersion = JBTextField("1.0.0")
    private val mainClass = JBTextField("com.example.App")
    private val mainJar = JBTextField()
    private val vendor = JBTextField("Visual Java")
    private val icon = JBTextField()
    private val outputDir = JBTextField("dist")
    private val packageType: JComboBox<String> = JComboBox(when (osName()) {
        OsKind.MAC -> arrayOf("app-image", "dmg", "pkg")
        OsKind.WINDOWS -> arrayOf("app-image", "exe", "msi")
        OsKind.LINUX -> arrayOf("app-image", "deb", "rpm")
        OsKind.OTHER -> arrayOf("app-image")
    })
    private val log = JBTextArea().apply { isEditable = false; lineWrap = true }

    init {
        title = "Package as Native App (jpackage)"
        seedDefaults()
        init()
    }

    private fun seedDefaults() {
        ReadAction.compute<Unit, RuntimeException> {
            val mod = ModuleManager.getInstance(project).modules.firstOrNull() ?: return@compute
            val basePath = mod.moduleFile?.parent?.path ?: project.basePath ?: return@compute
            val libs = java.io.File(basePath, "build/libs")
            if (libs.isDirectory) {
                val firstJar = libs.listFiles { f -> f.extension == "jar" }?.firstOrNull()
                if (firstJar != null) mainJar.text = firstJar.absolutePath
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("App name:", appName)
            .addLabeledComponent("Version:", appVersion)
            .addLabeledComponent("Vendor:", vendor)
            .addLabeledComponent("Main class:", mainClass)
            .addLabeledComponent("Main jar (full path):", mainJar)
            .addLabeledComponent("Icon (.icns / .ico / .png):", icon)
            .addLabeledComponent("Output directory (relative):", outputDir)
            .addLabeledComponent("Package type:", packageType)
            .panel
            .apply { border = JBUI.Borders.empty(8) }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(620, 520)
            add(form, BorderLayout.NORTH)
            add(JBLabel("Output log:").apply { border = JBUI.Borders.empty(4, 8) }, BorderLayout.CENTER)
            add(JBScrollPane(log).apply { preferredSize = Dimension(600, 280) }, BorderLayout.SOUTH)
        }
    }

    override fun doOKAction() {
        runJPackage()
        // Don't dismiss — let the user watch the log, click Cancel when done.
    }

    private fun runJPackage() {
        val jar = mainJar.text.trim()
        if (jar.isBlank() || !java.io.File(jar).exists()) {
            Messages.showErrorDialog(project, "Main jar not found: $jar", "jpackage")
            return
        }
        val jarFile = java.io.File(jar)
        val outDir = java.io.File(project.basePath ?: ".", outputDir.text.trim())
        outDir.mkdirs()

        val args = mutableListOf(
            "jpackage",
            "--name", appName.text.trim(),
            "--app-version", appVersion.text.trim(),
            "--vendor", vendor.text.trim(),
            "--input", jarFile.parentFile.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", mainClass.text.trim(),
            "--type", packageType.selectedItem as String,
            "--dest", outDir.absolutePath,
        )
        if (icon.text.isNotBlank()) { args += "--icon"; args += icon.text.trim() }

        log.append("\$ ${args.joinToString(" ")}\n")
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val pb = ProcessBuilder(args).redirectErrorStream(true)
                pb.directory(project.basePath?.let { java.io.File(it) } ?: java.io.File("."))
                val proc = pb.start()
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        ApplicationManager.getApplication().invokeLater { log.append("$line\n") }
                    }
                }
                val code = proc.waitFor()
                ApplicationManager.getApplication().invokeLater {
                    log.append(if (code == 0) "\n✅ jpackage finished. Output in: ${outDir.absolutePath}\n"
                               else "\n❌ jpackage exit $code\n")
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    log.append("\n❌ ${e.javaClass.simpleName}: ${e.message}\n")
                }
            }
        }
    }

    private enum class OsKind { MAC, WINDOWS, LINUX, OTHER }
    private fun osName(): OsKind {
        val n = System.getProperty("os.name", "").lowercase()
        return when {
            "mac" in n || "darwin" in n -> OsKind.MAC
            "win" in n -> OsKind.WINDOWS
            "linux" in n -> OsKind.LINUX
            else -> OsKind.OTHER
        }
    }
}
