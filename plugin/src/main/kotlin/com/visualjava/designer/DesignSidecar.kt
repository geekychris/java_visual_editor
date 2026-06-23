package com.visualjava.designer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Tiny JSON sidecar that stores designer-only state next to each FXML:
 *   Hello.fxml          ← source of truth (PSI)
 *   Hello.fxml.design.json   ← per-form designer state
 *
 * The plan's full sidecar schema includes `pendingEventBindings` and
 * `v2Hints`; for now we only persist what the designer needs to round-trip:
 * ruler guides. Extra fields read from disk are preserved on write so a
 * future schema bump doesn't lose data.
 */
class DesignSidecarData() {
    var verticalGuides: List<Int> = emptyList()
    var horizontalGuides: List<Int> = emptyList()

    constructor(v: List<Int>, h: List<Int>) : this() {
        verticalGuides = v
        horizontalGuides = h
    }
}

object DesignSidecar {
    private val mapper: ObjectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun sidecarFor(fxml: VirtualFile): File =
        File(fxml.path + ".design.json")

    fun load(fxml: VirtualFile): DesignSidecarData {
        val f = sidecarFor(fxml)
        if (!f.exists() || f.length() == 0L) return DesignSidecarData()
        return runCatching { mapper.readValue(f, DesignSidecarData::class.java) }
            .getOrElse { DesignSidecarData() }
    }

    fun save(fxml: VirtualFile, data: DesignSidecarData) {
        val f = sidecarFor(fxml)
        f.parentFile?.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(f, data)
        // Make IntelliJ aware of the new file so it shows up in the project view.
        VfsUtil.markDirtyAndRefresh(true, false, false, f)
    }
}
