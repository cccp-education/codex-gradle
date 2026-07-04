package codex.ocr

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP implementation of [OllamaChatClient] — calls the Ollama `/api/chat` endpoint.
 *
 * Sends a vision chat request with base64-encoded images and returns the raw
 * text content from the model response. Uses [java.net.http.HttpClient] (JDK 11+)
 * to avoid pulling an extra HTTP library.
 *
 * Endpoint resolution order:
 * 1. [OcrConfig.endpoint] if non-null
 * 2. Default: `http://{host}:{port}/api/chat` where host/port come from the config
 *
 * @property host Ollama server host (default: localhost)
 * @property port Ollama server port (must be in the 11437-11465 rotation range)
 * @property connectTimeout HTTP connect timeout
 */
class HttpOllamaChatClient(
    private val host: String = "localhost",
    private val port: Int = 11437,
    private val connectTimeout: Duration = Duration.ofSeconds(30)
) : OllamaChatClient {

    private val log = LoggerFactory.getLogger(HttpOllamaChatClient::class.java)
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .build()

    override fun chat(config: OcrConfig, prompt: String, images: List<String>): String {
        val endpoint = resolveEndpoint(config)
        val payload = buildPayload(config, prompt, images)

        log.debug("[HttpOllama] POST {} model={} images={}", endpoint, config.model, images.size)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Ollama HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }

        return parseMessageContent(response.body())
    }

    private fun resolveEndpoint(config: OcrConfig): String {
        val base = config.endpoint
            ?: "http://$host:$port"
        return if (base.endsWith("/api/chat")) base else "$base/api/chat"
    }

    private fun buildPayload(config: OcrConfig, prompt: String, images: List<String>): String {
        val root = mapper.createObjectNode()
        root.put("model", config.model)
        root.put("stream", false)
        if (config.temperature > 0.0) root.put("temperature", config.temperature)
        if (config.maxTokens > 0) root.put("num_predict", config.maxTokens)

        val messagesArray = root.putArray("messages")
        val userMsg = messagesArray.addObject()
        userMsg.put("role", "user")
        userMsg.put("content", prompt)
        val imagesArray = userMsg.putArray("images")
        images.forEach { imagesArray.add(it) }

        return mapper.writeValueAsString(root)
    }

    private fun parseMessageContent(body: String): String {
        val root: JsonNode = mapper.readTree(body)
        val message = root.path("message")
        if (message.isMissingNode) {
            val direct = root.path("response")
            if (!direct.isMissingNode) return direct.asText("")
            throw RuntimeException("Ollama response missing message.content and response field")
        }
        return message.path("content").asText("")
    }
}