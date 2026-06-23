package com.visualjava.pojo

import com.visualjava.codegen.ControllerCodeGenerator
import com.visualjava.codegen.JavaFxTypeMap
import com.visualjava.preview.PreviewClient

/**
 * Generates bind/save methods that copy values between a chosen POJO class
 * and the form's controls.
 *
 * Style: we emit plain Java `bind(pojo)` + `save(pojo)` methods using the
 * widget's standard accessors (`getText`/`setText`, `isSelected`/
 * `setSelected`, …). Avoids the typed-binding gymnastics of mixing
 * `IntegerProperty` with `Spinner`'s `Object` valueFactory.
 */
class PojoBinder(private val codeGen: ControllerCodeGenerator) {

    /** One row of the wizard: a property mapped to an fx:id (or skipped). */
    data class Mapping(
        val property: PojoIntrospector.BeanProperty,
        val fxId: String,
        val tagName: String,
    )

    /**
     * @param controllerClass the target controller PsiClass
     * @param pojoFqn full FQN of the POJO class
     * @param mappings the property↔widget mappings the wizard collected
     */
    fun emit(
        controllerClass: com.intellij.psi.PsiClass,
        pojoFqn: String,
        mappings: List<Mapping>,
    ) {
        if (mappings.isEmpty()) return

        // Ensure @FXML fields exist for every mapped widget
        for (m in mappings) {
            codeGen.ensureField(controllerClass, m.fxId, m.tagName)
        }

        // bind(pojo)
        val bindBody = buildString {
            for (m in mappings) appendLine(bindLine(m))
        }
        codeGen.ensurePlainMethod(
            controllerClass, "bind",
            "public void bind($pojoFqn pojo)",
            bindBody.trimEnd(),
        )

        // save(pojo)
        val savable = mappings.filter { it.property.setterName != null }
        if (savable.isNotEmpty()) {
            val saveBody = buildString {
                for (m in savable) appendLine(saveLine(m))
            }
            codeGen.ensurePlainMethod(
                controllerClass, "save",
                "public void save($pojoFqn pojo)",
                saveBody.trimEnd(),
            )
        }
    }

    private fun bindLine(m: Mapping): String {
        val fx = m.fxId
        val gn = "pojo.${m.property.getterName}()"
        val ttag = m.tagName
        return when (m.property.kind) {
            PojoIntrospector.Kind.STRING -> when (ttag) {
                "TextField", "PasswordField", "TextArea" -> "$fx.setText($gn == null ? \"\" : $gn);"
                "Label", "Hyperlink" -> "$fx.setText($gn == null ? \"\" : $gn);"
                "ComboBox", "ChoiceBox" -> "$fx.setValue($gn);"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.BOOLEAN -> when (ttag) {
                "CheckBox", "ToggleButton", "RadioButton" -> "$fx.setSelected($gn);"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.INT, PojoIntrospector.Kind.LONG -> when (ttag) {
                "Spinner" -> "$fx.getValueFactory().setValue($gn);"
                "Slider" -> "$fx.setValue($gn);"
                "TextField" -> "$fx.setText(String.valueOf($gn));"
                "Label" -> "$fx.setText(String.valueOf($gn));"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.DOUBLE, PojoIntrospector.Kind.FLOAT -> when (ttag) {
                "Slider", "ProgressBar", "ProgressIndicator" -> "$fx.setProgress($gn);"
                "Spinner" -> "$fx.getValueFactory().setValue($gn);"
                "TextField" -> "$fx.setText(String.valueOf($gn));"
                "Label" -> "$fx.setText(String.valueOf($gn));"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.LOCAL_DATE -> when (ttag) {
                "DatePicker" -> "$fx.setValue($gn);"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.COLOR -> when (ttag) {
                "ColorPicker" -> "$fx.setValue($gn);"
                else -> "// TODO: bind ${m.property.name} to $fx"
            }
            PojoIntrospector.Kind.ENUM -> when (ttag) {
                "ComboBox", "ChoiceBox" -> "$fx.setValue($gn);"
                else -> "// TODO: bind ${m.property.name} (enum) to $fx"
            }
            PojoIntrospector.Kind.OTHER, PojoIntrospector.Kind.LOCAL_DATE_TIME ->
                "// TODO: bind ${m.property.name} (${m.property.typeShort}) to $fx"
        }
    }

    private fun saveLine(m: Mapping): String {
        val setter = "pojo.${m.property.setterName}"
        val fx = m.fxId
        val ttag = m.tagName
        return when (m.property.kind) {
            PojoIntrospector.Kind.STRING -> when (ttag) {
                "TextField", "PasswordField", "TextArea" -> "$setter($fx.getText());"
                "ComboBox", "ChoiceBox" -> "$setter(String.valueOf($fx.getValue()));"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.BOOLEAN -> when (ttag) {
                "CheckBox", "ToggleButton", "RadioButton" -> "$setter($fx.isSelected());"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.INT -> when (ttag) {
                "Spinner" -> "$setter(((Integer) $fx.getValueFactory().getValue()));"
                "Slider" -> "$setter((int) $fx.getValue());"
                "TextField" -> "$setter(Integer.parseInt($fx.getText()));"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.LONG -> when (ttag) {
                "Spinner" -> "$setter(((Long) $fx.getValueFactory().getValue()));"
                "TextField" -> "$setter(Long.parseLong($fx.getText()));"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.DOUBLE -> when (ttag) {
                "Slider" -> "$setter($fx.getValue());"
                "Spinner" -> "$setter(((Double) $fx.getValueFactory().getValue()));"
                "TextField" -> "$setter(Double.parseDouble($fx.getText()));"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.FLOAT -> when (ttag) {
                "Slider" -> "$setter((float) $fx.getValue());"
                "TextField" -> "$setter(Float.parseFloat($fx.getText()));"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.LOCAL_DATE -> when (ttag) {
                "DatePicker" -> "$setter($fx.getValue());"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.COLOR -> when (ttag) {
                "ColorPicker" -> "$setter($fx.getValue());"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.ENUM -> when (ttag) {
                "ComboBox", "ChoiceBox" -> "$setter((${m.property.typeFqn}) $fx.getValue());"
                else -> "// TODO: save ${m.property.name}"
            }
            PojoIntrospector.Kind.OTHER, PojoIntrospector.Kind.LOCAL_DATE_TIME ->
                "// TODO: save ${m.property.name}"
        }
    }

    /** Best default widget kind for a given property kind. Used by Form-from-POJO. */
    companion object {
        fun defaultWidgetFor(kind: PojoIntrospector.Kind): String = when (kind) {
            PojoIntrospector.Kind.STRING -> "TextField"
            PojoIntrospector.Kind.BOOLEAN -> "CheckBox"
            PojoIntrospector.Kind.INT, PojoIntrospector.Kind.LONG -> "Spinner"
            PojoIntrospector.Kind.DOUBLE, PojoIntrospector.Kind.FLOAT -> "Slider"
            PojoIntrospector.Kind.LOCAL_DATE -> "DatePicker"
            PojoIntrospector.Kind.LOCAL_DATE_TIME -> "DatePicker"
            PojoIntrospector.Kind.COLOR -> "ColorPicker"
            PojoIntrospector.Kind.ENUM -> "ComboBox"
            PojoIntrospector.Kind.OTHER -> "TextField"
        }
    }
}
