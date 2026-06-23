package com.visualjava.refactor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * Visual Java's tiny "Rename fx:id" refactoring.
 *
 * Updates, atomically in one undo step:
 *  1. The FXML element's `fx:id` attribute.
 *  2. The matching `@FXML` field declaration in the controller.
 *  3. Every controller method named `<oldFxId><EventName>` (the convention we
 *     generate) — renamed to `<newFxId><EventName>`.
 *  4. Every FXML attribute `on*="#<oldHandlerName>"` matching one of those
 *     methods — pointed at the renamed handler.
 *
 * Field/method/attribute references that don't follow our naming convention
 * are left alone (we don't try to do whole-program rename).
 */
class FxIdRenamer(private val project: Project) {

    /**
     * @return number of distinct text-level replacements (helps show a result
     *         summary). Throws [IllegalArgumentException] if the new name is
     *         invalid or already in use.
     */
    fun rename(fxmlFile: XmlFile, oldFxId: String, newFxId: String): Int {
        require(newFxId.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            "fx:id must be a valid Java identifier"
        }
        require(oldFxId != newFxId) { "New name equals old name" }
        require(findTagByFxId(fxmlFile, newFxId) == null) {
            "fx:id '$newFxId' is already used in this form"
        }
        val tag = findTagByFxId(fxmlFile, oldFxId)
            ?: throw IllegalArgumentException("fx:id '$oldFxId' not found")

        var count = 0
        WriteCommandAction.runWriteCommandAction(project, "Rename fx:id $oldFxId → $newFxId", null, {
            // 1) Rename the FXML attribute
            tag.setAttribute("fx:id", newFxId)
            count++

            // 2/3/4) Look at the controller (if there's one set).
            val controller = resolveController(fxmlFile)
            if (controller != null) {
                // Field: name == oldFxId
                controller.findFieldByName(oldFxId, false)?.let { field ->
                    field.setName(newFxId)
                    count++
                }
                // Methods: name starts with oldFxId
                val renamedMethods = mutableListOf<Pair<String, String>>()
                for (method in controller.methods.toList()) {
                    val name = method.name
                    if (name.startsWith(oldFxId) && name.length > oldFxId.length) {
                        val suffix = name.substring(oldFxId.length)
                        if (suffix.isNotEmpty() && suffix[0].isUpperCase()) {
                            val newName = newFxId + suffix
                            renamedMethods += name to newName
                            method.setName(newName)
                            count++
                        }
                    }
                }
                // 4) Rewrite FXML on*="#oldName" references to renamed methods
                val all = collectTags(fxmlFile)
                for ((oldName, newName) in renamedMethods) {
                    for (t in all) {
                        for (attr in t.attributes) {
                            val v = attr.value ?: continue
                            if (v == "#$oldName") {
                                attr.setValue("#$newName")
                                count++
                            }
                        }
                    }
                }
            }
        })
        return count
    }

    private fun resolveController(fxmlFile: XmlFile): PsiClass? {
        val fqn = fxmlFile.rootTag?.getAttributeValue("fx:controller") ?: return null
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project))
    }

    private fun findTagByFxId(fxmlFile: XmlFile, fxId: String): XmlTag? {
        val root = fxmlFile.rootTag ?: return null
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == fxId }
    }

    private fun collectTags(fxmlFile: XmlFile): List<XmlTag> {
        val root = fxmlFile.rootTag ?: return emptyList()
        return walk(root).toList()
    }

    private fun walk(tag: XmlTag): Sequence<XmlTag> = sequence {
        yield(tag)
        for (child in tag.subTags) yieldAll(walk(child))
    }
}
