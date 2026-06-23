package com.visualjava.codegen

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * PSI-driven controller manipulation.
 *
 * The iron rule: never overwrite an existing field or method body. We add
 * what's missing and stop. Hand-edited controller code is sacred.
 *
 * All mutations expect to run inside a [com.intellij.openapi.command.WriteCommandAction].
 */
class ControllerCodeGenerator(private val project: Project) {

    private val factory = JavaPsiFacade.getElementFactory(project)
    private val javaStyle = JavaCodeStyleManager.getInstance(project)
    private val codeStyle = CodeStyleManager.getInstance(project)

    fun ensureField(psiClass: PsiClass, fxId: String, fxmlTag: String): PsiField {
        psiClass.findFieldByName(fxId, false)?.let { return it }

        val typeFqn = JavaFxTypeMap.resolve(fxmlTag)
        val text = "@javafx.fxml.FXML private $typeFqn $fxId;"
        val field = factory.createFieldFromText(text, psiClass)
        val added = psiClass.add(field) as PsiField
        javaStyle.shortenClassReferences(added)
        codeStyle.reformat(added)
        return added
    }

    fun ensureHandler(psiClass: PsiClass, methodName: String, eventClassFqn: String): PsiMethod {
        psiClass.findMethodsByName(methodName, false).firstOrNull()?.let { return it }

        val text = buildString {
            append("@javafx.fxml.FXML\n")
            append("private void $methodName($eventClassFqn event) {\n")
            append("}\n")
        }
        val method = factory.createMethodFromText(text, psiClass)
        val added = psiClass.add(method) as PsiMethod
        javaStyle.shortenClassReferences(added)
        codeStyle.reformat(added)
        return added
    }

    /**
     * Like [ensureHandler] but seeds the body with [bodyCode] on first creation.
     * Existing methods are returned untouched (the iron rule).
     */
    fun ensureHandlerWithBody(
        psiClass: PsiClass,
        methodName: String,
        eventClassFqn: String,
        bodyCode: String,
    ): PsiMethod {
        psiClass.findMethodsByName(methodName, false).firstOrNull()?.let { return it }
        val text = buildString {
            append("@javafx.fxml.FXML\n")
            append("private void $methodName($eventClassFqn event) {\n")
            append(bodyCode.trimEnd()).append("\n")
            append("}\n")
        }
        val method = factory.createMethodFromText(text, psiClass)
        val added = psiClass.add(method) as PsiMethod
        javaStyle.shortenClassReferences(added)
        codeStyle.reformat(added)
        return added
    }

    /**
     * Ensure a plain (non-@FXML) method exists with the given signature and body.
     * Used by recipes for callback stubs and small helpers (`handleSubmit`,
     * `onFileDropped`, `toHex`, …). Doesn't touch existing definitions.
     *
     * [signature] is the full prefix up to the body, e.g.
     *   `private void handleSubmit()` or
     *   `private String toHex(javafx.scene.paint.Color color)`.
     */
    fun ensurePlainMethod(
        psiClass: PsiClass,
        methodName: String,
        signature: String,
        bodyCode: String,
    ): PsiMethod {
        psiClass.findMethodsByName(methodName, false).firstOrNull()?.let { return it }
        val text = buildString {
            append(signature).append(" {\n")
            if (bodyCode.isNotBlank()) append(bodyCode.trimEnd()).append("\n")
            append("}\n")
        }
        val method = factory.createMethodFromText(text, psiClass)
        val added = psiClass.add(method) as PsiMethod
        javaStyle.shortenClassReferences(added)
        codeStyle.reformat(added)
        return added
    }

    /**
     * Ensure an `@FXML private void initialize() { }` exists. Returns the
     * (existing or just-created) method. Used by binding recipes that need
     * to append setup code in the FXML lifecycle hook.
     */
    fun ensureInitialize(psiClass: PsiClass): PsiMethod {
        psiClass.findMethodsByName("initialize", false).firstOrNull()?.let { return it }
        val text = buildString {
            append("@javafx.fxml.FXML\n")
            append("private void initialize() {\n")
            append("}\n")
        }
        val method = factory.createMethodFromText(text, psiClass)
        val added = psiClass.add(method) as PsiMethod
        javaStyle.shortenClassReferences(added)
        codeStyle.reformat(added)
        return added
    }

    /**
     * Append a Java statement to the end of [method]'s body. Used by recipes
     * to incrementally add bindings/setup to `initialize()`.
     *
     * Idempotency: if a statement with identical text already exists in the
     * body, skip — so re-running a recipe doesn't duplicate the binding.
     */
    fun appendStatement(method: PsiMethod, statementText: String) {
        val body = method.body ?: return
        val normalised = statementText.trim().trimEnd(';') + ";"
        val existing = body.statements.any { it.text.trim().trimEnd(';') + ";" == normalised }
        if (existing) return
        val statement = factory.createStatementFromText(normalised, method)
        body.add(statement)
        javaStyle.shortenClassReferences(body)
        codeStyle.reformat(body)
    }

    /** Sets `<eventProperty>="#<methodName>"` on the FXML element with [fxId]. */
    fun wireFxmlEvent(fxmlFile: XmlFile, fxId: String, eventProperty: String, methodName: String) {
        val tag = findTagByFxId(fxmlFile, fxId) ?: return
        tag.setAttribute(eventProperty, "#$methodName")
    }

    /** Look up an FXML tag by its fx:id (depth-first). */
    fun findTagByFxId(fxmlFile: XmlFile, fxId: String): XmlTag? {
        val root = fxmlFile.rootTag ?: return null
        return walk(root).firstOrNull { it.getAttributeValue("fx:id") == fxId }
    }

    private fun walk(root: XmlTag): Sequence<XmlTag> = sequence {
        yield(root)
        for (child in root.subTags) yieldAll(walk(child))
    }
}
