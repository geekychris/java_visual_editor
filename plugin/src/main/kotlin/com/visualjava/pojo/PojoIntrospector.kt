package com.visualjava.pojo

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

/**
 * Discovers bean-like properties on a Java class: a getter + setter pair, with
 * optional JavaFX `Property<T>` method.
 *
 * Built specifically for what the POJO binding wizard / form-from-POJO
 * generator need — we don't claim to be a full beans-introspection library.
 */
class PojoIntrospector(private val project: Project) {

    enum class Kind {
        STRING, BOOLEAN, INT, LONG, DOUBLE, FLOAT,
        LOCAL_DATE, LOCAL_DATE_TIME, COLOR, ENUM, OTHER,
    }

    data class BeanProperty(
        val name: String,
        /** Capitalized for getter/setter names: e.g., name = "title" → "Title". */
        val nameCap: String,
        val typeFqn: String,
        val typeShort: String,
        val kind: Kind,
        val getterName: String,
        val setterName: String?,
        /** Non-null if the class also exposes a JavaFX-style xxxProperty() method. */
        val propertyMethodName: String?,
        /** Names of enum constants when [kind] == ENUM. */
        val enumConstants: List<String> = emptyList(),
    )

    fun findClass(fqn: String): PsiClass? =
        JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))

    fun findProperties(cls: PsiClass): List<BeanProperty> {
        val getters = mutableMapOf<String, PsiMethod>() // "Title" → getTitle / isTitle
        val setters = mutableMapOf<String, PsiMethod>() // "Title" → setTitle
        val propertyMethods = mutableMapOf<String, PsiMethod>() // "title" → titleProperty()

        for (m in cls.allMethods) {
            if (m.hasModifierProperty("static")) continue
            val name = m.name
            when {
                name.startsWith("get") && name.length > 3 && m.parameterList.parametersCount == 0 ->
                    getters[name.substring(3)] = m
                name.startsWith("is") && name.length > 2 && m.parameterList.parametersCount == 0 &&
                    (m.returnType == PsiType.BOOLEAN || m.returnType?.canonicalText == "java.lang.Boolean") ->
                    getters[name.substring(2)] = m
                name.startsWith("set") && name.length > 3 && m.parameterList.parametersCount == 1 ->
                    setters[name.substring(3)] = m
                name.endsWith("Property") && name.length > "Property".length &&
                    m.parameterList.parametersCount == 0 -> {
                    val propName = name.removeSuffix("Property")
                    propertyMethods[propName] = m
                }
            }
        }

        return getters.keys.sortedBy { it.lowercase() }.mapNotNull { cap ->
            val getter = getters[cap] ?: return@mapNotNull null
            val type = getter.returnType ?: return@mapNotNull null
            val name = cap[0].lowercaseChar() + cap.substring(1)
            val (kind, enumConstants) = classifyType(type)
            BeanProperty(
                name = name,
                nameCap = cap,
                typeFqn = type.canonicalText,
                typeShort = type.presentableText,
                kind = kind,
                getterName = getter.name,
                setterName = setters[cap]?.name,
                propertyMethodName = propertyMethods[name]?.name,
                enumConstants = enumConstants,
            )
        }
    }

    private fun classifyType(t: PsiType): Pair<Kind, List<String>> {
        val canonical = t.canonicalText
        return when (canonical) {
            "java.lang.String" -> Kind.STRING to emptyList()
            "boolean", "java.lang.Boolean" -> Kind.BOOLEAN to emptyList()
            "int", "java.lang.Integer" -> Kind.INT to emptyList()
            "long", "java.lang.Long" -> Kind.LONG to emptyList()
            "double", "java.lang.Double" -> Kind.DOUBLE to emptyList()
            "float", "java.lang.Float" -> Kind.FLOAT to emptyList()
            "java.time.LocalDate" -> Kind.LOCAL_DATE to emptyList()
            "java.time.LocalDateTime" -> Kind.LOCAL_DATE_TIME to emptyList()
            "javafx.scene.paint.Color" -> Kind.COLOR to emptyList()
            else -> {
                val psiClass = (t as? com.intellij.psi.PsiClassType)?.resolve()
                if (psiClass != null && psiClass.isEnum) {
                    val constants = psiClass.fields.filter { it.hasModifierProperty("static") && it.name.matches(Regex("[A-Z_]+")) }
                        .map { it.name }
                    Kind.ENUM to constants
                } else {
                    Kind.OTHER to emptyList()
                }
            }
        }
    }
}
