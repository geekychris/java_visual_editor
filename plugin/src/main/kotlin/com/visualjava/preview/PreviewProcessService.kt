package com.visualjava.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the sidecar JavaFX renderer JVM for a project.
 *
 * Lazy lifecycle: the first call to [client] launches the renderer and waits
 * for it to print "PORT=<n>" on stdout, then opens a [PreviewClient]. Subsequent
 * calls reuse the same client. On project close, [dispose] tears it all down.
 */
@Service(Service.Level.PROJECT)
class PreviewProcessService(private val project: Project) : Disposable {

    private val log = thisLogger()
    private val state = AtomicReference<State>(State.Idle)

    private sealed interface State {
        data object Idle : State
        data class Running(val process: Process, val client: PreviewClient) : State
    }

    @Synchronized
    fun client(): PreviewClient {
        when (val s = state.get()) {
            is State.Running -> if (s.process.isAlive) return s.client
            else -> Unit
        }
        return start().also { /* swap inside start() */ }
    }

    @Synchronized
    private fun start(): PreviewClient {
        // If a stale Running entry is here (dead process), clear it.
        (state.get() as? State.Running)?.let {
            runCatching { it.client.close() }
            it.process.destroyForcibly()
        }

        val jarPath = extractRendererJar()
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString()

        val pb = ProcessBuilder(javaBin, "-jar", jarPath.toString())
            .redirectErrorStream(false)
        log.info("Launching preview renderer: ${pb.command()}")
        val process = pb.start()

        val port = readPort(process)
        log.info("Preview renderer ready on port $port")

        val client = PreviewClient("127.0.0.1", port)
        state.set(State.Running(process, client))
        return client
    }

    private fun readPort(process: Process): Int {
        val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
        // Drain stdout in a daemon thread once we've consumed the PORT line, so the
        // child's pipe never fills up.
        val portLine = generateSequence { reader.readLine() }
            .firstOrNull { it.startsWith("PORT=") }
            ?: error("Renderer exited before emitting PORT line")

        // Drain remainder in background.
        Thread({
            try {
                reader.use { r -> r.forEachLine { log.debug("[renderer] $it") } }
            } catch (_: Exception) {
            }
        }, "visual-java-renderer-stdout").apply { isDaemon = true; start() }

        Thread({
            try {
                BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
                    .use { r -> r.forEachLine { log.warn("[renderer:stderr] $it") } }
            } catch (_: Exception) {
            }
        }, "visual-java-renderer-stderr").apply { isDaemon = true; start() }

        return portLine.removePrefix("PORT=").trim().toInt()
    }

    private fun extractRendererJar(): Path {
        val cacheDir = Path.of(PathManager.getSystemPath(), "visual-java")
        Files.createDirectories(cacheDir)
        val target = cacheDir.resolve("preview-renderer.jar")

        // Re-extract on every plugin start: cheap (~10MB) and ensures version sync after upgrades.
        javaClass.getResourceAsStream("/preview-renderer/preview-renderer.jar")
            ?.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
            ?: error("Bundled preview-renderer.jar not found on classpath")
        return target
    }

    override fun dispose() {
        (state.getAndSet(State.Idle) as? State.Running)?.let { running ->
            runCatching { running.client.shutdown() }
            runCatching { running.client.close() }
            if (!running.process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                running.process.destroyForcibly()
            }
        }
    }

    companion object {
        fun getInstance(project: Project): PreviewProcessService = project.getService(PreviewProcessService::class.java)
    }
}
