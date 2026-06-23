package com.visualjava.preview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Synchronous client to a [PreviewRenderer] over a persistent localhost socket.
 *
 * Single-connection, single-threaded; a project's [PreviewProcessService]
 * serialises calls. Higher-level concurrency lives in the editor layer.
 */
class PreviewClient(host: String, port: Int) : Closeable {

    private val socket = Socket(host, port)
    private val out: BufferedWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
    private val `in`: BufferedReader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
    private val mapper = ObjectMapper()

    data class NodeBounds(
        val fxId: String,
        val tagName: String,
        val parentFxId: String?,
        val x: Double,
        val y: Double,
        val w: Double,
        val h: Double,
    )

    data class Frame(val image: BufferedImage, val nodes: List<NodeBounds>)

    @Synchronized
    fun render(fxml: String, width: Int, height: Int, stylesheetUrls: List<String> = emptyList()): Frame {
        val req = mapper.createObjectNode().apply {
            put("op", "render")
            put("fxml", fxml)
            put("width", width)
            put("height", height)
            if (stylesheetUrls.isNotEmpty()) {
                val arr = putArray("stylesheets")
                for (u in stylesheetUrls) arr.add(u)
            }
        }
        val resp = call(mapper.writeValueAsString(req))
        if (resp.path("op").asText() != "frame") {
            error("Renderer error: ${resp.path("message").asText()}")
        }
        val png = Base64.getDecoder().decode(resp.path("pngBase64").asText())
        val image = ImageIO.read(png.inputStream())
            ?: error("Renderer returned an unreadable image")
        val nodes = resp.path("nodes").map {
            NodeBounds(
                fxId = it.path("fxId").asText(),
                tagName = it.path("tagName").asText(""),
                parentFxId = it.path("parentFxId").asText("").ifEmpty { null },
                x = it.path("x").asDouble(),
                y = it.path("y").asDouble(),
                w = it.path("w").asDouble(),
                h = it.path("h").asDouble(),
            )
        }
        return Frame(image, nodes)
    }

    @Synchronized
    fun shutdown() {
        runCatching { call("""{"op":"shutdown"}""") }
    }

    private fun call(line: String): JsonNode {
        out.write(line)
        out.write("\n")
        out.flush()
        val reply = `in`.readLine() ?: error("Renderer closed the connection")
        return mapper.readTree(reply)
    }

    override fun close() {
        runCatching { out.close() }
        runCatching { `in`.close() }
        runCatching { socket.close() }
    }
}
