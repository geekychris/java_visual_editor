package com.visualjava.codegen

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Resolves (and creates, if missing) the Java controller class for an FXML file.
 *
 * - If `fx:controller="com.foo.Bar"` is set and resolves on the classpath, returns it.
 * - Otherwise: creates `<FxmlName>Controller.java` in the module's Java source root,
 *   mirroring the FXML's sub-path under its resource root, and writes the
 *   `fx:controller="..."` attribute back to the FXML.
 *
 * **Never writes a .java file into a resources root** — even when index state is
 * incomplete or the FXML lives somewhere odd. Throws [NoSuitableTargetException]
 * instead so the caller can show a clear error.
 *
 * All mutations expect to run inside a [com.intellij.openapi.command.WriteCommandAction].
 */
class FxmlControllerResolver(private val project: Project) {

    class NoSuitableTargetException(message: String) : RuntimeException(message)

    fun findController(fxmlFile: XmlFile): PsiClass? {
        val fqn = fxmlFile.rootTag?.getAttributeValue("fx:controller") ?: return null
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project))
    }

    fun findOrCreateController(fxmlFile: XmlFile): PsiClass {
        findController(fxmlFile)?.let { return it }

        val fxmlVf = fxmlFile.virtualFile
        val controllerName = fxmlVf.nameWithoutExtension + "Controller"
        val (targetDir, packageName) = pickTargetDir(fxmlVf)

        val psiDir = PsiManager.getInstance(project).findDirectory(targetDir)
            ?: throw NoSuitableTargetException("Cannot resolve PSI directory for ${targetDir.path}")

        // Reuse an existing class with the same name in the target dir if one's there
        // (e.g., from a prior failed run that wrote the wrong fx:controller FQN).
        val existing = psiDir.files
            .filterIsInstance<PsiJavaFile>()
            .flatMap { it.classes.toList() }
            .firstOrNull { it.name == controllerName }
        val cls = existing ?: JavaDirectoryService.getInstance().createClass(psiDir, controllerName)

        val fqn = if (packageName.isEmpty()) controllerName else "$packageName.$controllerName"
        fxmlFile.rootTag?.setAttribute("fx:controller", fqn)

        return cls
    }

    private fun pickTargetDir(fxmlVf: VirtualFile): Pair<VirtualFile, String> {
        val module = ModuleUtilCore.findModuleForFile(fxmlVf, project)
            ?: throw NoSuitableTargetException(
                "The FXML file is not part of any project module. " +
                    "Make sure the project has been imported successfully."
            )

        val roots = ModuleRootManager.getInstance(module)
        val javaRoots = roots.getSourceRoots(JavaSourceRootType.SOURCE)
        if (javaRoots.isEmpty()) {
            throw NoSuitableTargetException(
                "Module '${module.name}' has no Java source root. " +
                    "Add a src/main/java directory (or wait for the Gradle/Maven sync to finish), then try again."
            )
        }

        val resourceRoots = roots.getSourceRoots(JavaResourceRootType.RESOURCE)
        val containingRes = resourceRoots.firstOrNull { VfsUtilCore.isAncestor(it, fxmlVf, false) }
        if (containingRes != null) {
            // FXML in src/main/resources/<pkg>/Form.fxml → controller in src/main/java/<pkg>/FormController.java.
            val rel = VfsUtilCore.getRelativePath(fxmlVf.parent, containingRes, '/').orEmpty()
            val parts = rel.split('/').filter { it.isNotEmpty() }
            val javaTarget = ensureSubdir(javaRoots.first(), parts)
            return javaTarget to parts.joinToString(".")
        }

        // FXML not in a resources root. Maybe it's loose alongside Java sources?
        val fxmlParent = fxmlVf.parent ?: throw NoSuitableTargetException("FXML file has no parent directory")
        val containingJava = javaRoots.firstOrNull { VfsUtilCore.isAncestor(it, fxmlParent, false) }
        if (containingJava != null) {
            val rel = VfsUtilCore.getRelativePath(fxmlParent, containingJava, '/').orEmpty()
            val pkg = rel.split('/').filter { it.isNotEmpty() }.joinToString(".")
            return fxmlParent to pkg
        }

        // FXML is somewhere outside any recognised source root — fall back to the
        // top of the first Java source root. Never silently write into resources/.
        return javaRoots.first() to ""
    }

    private fun ensureSubdir(root: VirtualFile, parts: List<String>): VirtualFile {
        var cur = root
        for (part in parts) {
            cur = cur.findChild(part) ?: cur.createChildDirectory(this, part)
        }
        return cur
    }
}
