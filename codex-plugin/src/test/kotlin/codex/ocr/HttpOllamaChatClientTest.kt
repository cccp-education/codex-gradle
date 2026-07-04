package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpOllamaChatClientTest {

    @Test
    fun `chat sends POST to api chat endpoint and returns message content`() {
        val server = stubServer(200, """{"message":{"role":"assistant","content":"= Hello\n\nWorld"}}""")
        server.start()
        try {
            val port = server.address.port
            val client = HttpOllamaChatClient(host = "localhost", port = port, connectTimeout = Duration.ofSeconds(5))
            val config = OcrConfig(provider = "ollama", model = "gpt-oss:120b-cloud")

            val text = client.chat(config, "Extract text", listOf("data:image/png;base64,AAAA"))

            assertEquals("= Hello\n\nWorld", text)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `chat throws on non 2xx response`() {
        val server = stubServer(500, """{"error":"model not found"}""")
        server.start()
        try {
            val port = server.address.port
            val client = HttpOllamaChatClient(host = "localhost", port = port, connectTimeout = Duration.ofSeconds(5))
            val config = OcrConfig(provider = "ollama", model = "unknown-model")

            assertThrows(RuntimeException::class.java) {
                client.chat(config, "Extract", listOf("data:image/png;base64,BB"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `chat supports legacy response field when message missing`() {
        val server = stubServer(200, """{"response":"legacy content"}""")
        server.start()
        try {
            val port = server.address.port
            val client = HttpOllamaChatClient(host = "localhost", port = port)
            val config = OcrConfig(provider = "ollama", model = "stub")

            val text = client.chat(config, "p", emptyList())

            assertEquals("legacy content", text)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `chat uses config endpoint when provided`() {
        val server = stubServer(200, """{"message":{"content":"ok"}}""")
        server.start()
        try {
            val port = server.address.port
            val client = HttpOllamaChatClient(host = "localhost", port = 99999)
            val config = OcrConfig(
                provider = "ollama",
                model = "gpt-oss",
                endpoint = "http://localhost:$port"
            )

            val text = client.chat(config, "p", emptyList())

            assertEquals("ok", text)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `chat payload contains model and images fields`(@TempDir tempDir: File) {
        val captured = CapturingServer()
        captured.start()
        try {
            val port = captured.address.port
            val client = HttpOllamaChatClient(host = "localhost", port = port)
            val config = OcrConfig(provider = "ollama", model = "gpt-oss:120b-cloud")

            client.chat(config, "Extract and structure", listOf("data:image/png;base64,XYZ"))

            val body = captured.lastBody!!
            assertTrue(body.contains("\"model\""), "Payload should contain model field")
            assertTrue(body.contains("gpt-oss:120b-cloud"), "Payload should contain model name")
            assertTrue(body.contains("\"images\""), "Payload should contain images array")
            assertTrue(body.contains("data:image/png;base64,XYZ"), "Payload should contain base64 image")
            assertTrue(body.contains("\"stream\"") && body.contains("false"), "Payload should disable streaming")
        } finally {
            captured.stop(0)
        }
    }

    private fun stubServer(status: Int, body: String): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        return server
    }

    private class CapturingServer {
        private val server = HttpServer.create(InetSocketAddress(0), 0)
        var lastBody: String? = null

        init {
            server.createContext("/") { exchange ->
                lastBody = exchange.requestBody.bufferedReader().readText()
                val resp = """{"message":{"content":"captured"}}""".toByteArray()
                exchange.sendResponseHeaders(200, resp.size.toLong())
                exchange.responseBody.use { it.write(resp) }
            }
        }

        val address get() = server.address
        fun start() = server.start()
        fun stop(delay: Int) = server.stop(delay)
    }
}