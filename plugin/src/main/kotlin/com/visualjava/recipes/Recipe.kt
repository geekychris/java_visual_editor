package com.visualjava.recipes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlFile
import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.preview.PreviewClient

/**
 * A "wire-up recipe" — a pre-baked code generator that connects a few
 * widgets to each other or to standard JavaFX patterns (close window, open
 * file chooser, bind label to slider, …).
 *
 * The user picks a recipe from the dialog, assigns each [RecipeRole] to one
 * of the form's existing components, and the plugin emits the corresponding
 * controller code + FXML wiring.
 */
interface Recipe {
    val id: String
    val name: String
    val description: String
    val roles: List<RecipeRole>

    fun generate(ctx: RecipeContext, roleAssignments: Map<String, String>)
}

/**
 * One slot to fill before a recipe can run.
 *
 * [allowedTags] is the set of FXML widget tags that are acceptable for this
 * role. Empty set means any widget is fine.
 */
data class RecipeRole(
    val key: String,
    val displayName: String,
    val description: String,
    val allowedTags: Set<String> = emptySet(),
    val optional: Boolean = false,
) {
    fun accepts(tagName: String): Boolean = allowedTags.isEmpty() || tagName in allowedTags
}

data class RecipeContext(
    val project: Project,
    val fxmlFile: XmlFile,
    val controllerClass: PsiClass,
    val codeGen: ControllerCodeGenerator,
    val nodesByFxId: Map<String, PreviewClient.NodeBounds>,
)
