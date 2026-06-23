package com.visualjava.palette

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Per-project registry of user-authored palette entries.
 *
 * Stored as JSON at `.idea/visualjava-custom-controls.json` so it follows the
 * project and is shareable via VCS. Each entry contributes one ComponentDescriptor
 * to the palette — same drop/insert pipeline as the built-in widgets.
 */
class CustomControlEntry() {
    var tagName: String = ""
    var displayName: String = ""
    var importFqn: String = ""
    var defaultAttrs: Map<String, String> = emptyMap()
    var bodyXml: String? = null
    var extraImports: List<String> = emptyList()
}

@Service(Service.Level.PROJECT)
class CustomControlsRegistry(private val project: Project) {

    private val mapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun storeFile(): File {
        val ideaDir = File(project.basePath ?: ".", ".idea")
        if (!ideaDir.exists()) ideaDir.mkdirs()
        return File(ideaDir, "visualjava-custom-controls.json")
    }

    fun load(): List<CustomControlEntry> {
        val f = storeFile()
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = mapper.readValue(f, Array<CustomControlEntry>::class.java)
            arr.toList()
        }.getOrElse { emptyList() }
    }

    fun save(entries: List<CustomControlEntry>) {
        mapper.writerWithDefaultPrettyPrinter().writeValue(storeFile(), entries)
    }

    fun add(entry: CustomControlEntry) {
        val current = load().toMutableList()
        current.removeAll { it.tagName == entry.tagName }  // replace by tag
        current += entry
        save(current)
    }

    /** Convert stored entries to ComponentDescriptors for the palette. */
    fun asDescriptors(): List<ComponentDescriptor> = load().map {
        ComponentDescriptor(
            tagName = it.tagName,
            displayName = it.displayName.ifBlank { it.tagName },
            importFqn = it.importFqn,
            category = ComponentDescriptor.Category.CONTROLS,
            defaultAttrs = it.defaultAttrs,
            bodyXml = it.bodyXml,
            extraImports = it.extraImports,
        )
    }

    companion object {
        fun getInstance(project: Project): CustomControlsRegistry =
            project.getService(CustomControlsRegistry::class.java)
    }
}
