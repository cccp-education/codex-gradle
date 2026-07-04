package codex.ocr

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Base64

/**
 * LLM-based OCR engine — extracts structured AsciiDoc text from images via Ollama vision.
 *
 * First link of the OCR fallback chain: Gemini (cloud) → Ollama (cloud) → Tesseract (local).
 * Unlike [TesseractOcrEngine] which returns raw text, this engine produces AsciiDoc-structured
 * output (section headers, code blocks, tables) suitable for direct ingestion by the document
 * pipeline (DOC-11).
 *
 * Best-effort: on HTTP failure or empty response, returns an [OcrResult] with empty text and
 * zero confidence (does not throw). The caller ([OcrPipeline]) is responsible for falling back
 * to the next engine in the chain.
 *
 * @property client HTTP client abstraction for Ollama chat endpoint
 * @property config OCR provider/model settings
 */
class LlmOcrEngine(
    private val client: OllamaChatClient,
    private val config: OcrConfig = OcrConfig.defaultConfig()
) : OcrEngine {

    private val log = LoggerFactory.getLogger(LlmOcrEngine::class.java)

    override fun process(request: OcrRequest): OcrResult {
        if (request.imageData.isEmpty()) {
            return emptyResult(request)
        }

        val prompt = buildPrompt(request)
        val images = listOf(encodeImage(request))

        return try {
            val raw = client.chat(config, prompt, images)
            if (raw.isBlank()) {
                log.warn("[LlmOcr] Empty response from {} model {}", config.provider, config.model)
                return emptyResult(request)
            }
            OcrResult(
                structuredText = raw.trim(),
                confidence = 0.9,
                language = request.language,
                sourceFormat = request.format,
                generatedAt = Instant.now().toString(),
                model = config.model,
                metadata = mapOf(
                    "engine" to config.provider,
                    "model" to config.model
                )
            )
        } catch (e: Exception) {
            log.warn("[LlmOcr] LLM call failed: {}", e.message)
            emptyResult(request)
        }
    }

    private fun buildPrompt(request: OcrRequest): String {
        val base = request.prompt
            ?: "Extract all visible text from the image and structure it as AsciiDoc. " +
                "Use = for section titles, code blocks for code, tables for tabular data."
        return "$base\nLanguage: ${request.language}"
    }

    private fun encodeImage(request: OcrRequest): String {
        val base64 = Base64.getEncoder().encodeToString(request.imageData)
        val mime = request.format.substringAfter("image/", "png")
        return "data:image/$mime;base64,$base64"
    }

    private fun emptyResult(request: OcrRequest): OcrResult = OcrResult(
        structuredText = "",
        confidence = 0.0,
        language = request.language,
        sourceFormat = request.format,
        generatedAt = Instant.now().toString(),
        model = config.model,
        metadata = mapOf("engine" to config.provider, "status" to "failed")
    )
}