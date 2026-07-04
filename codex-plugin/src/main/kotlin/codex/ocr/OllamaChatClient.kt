package codex.ocr

/**
 * Port — Ollama chat client for vision OCR.
 *
 * Abstracts the HTTP call to Ollama `/api/chat` endpoint so the
 * [LlmOcrEngine] remains testable without a running Ollama instance.
 *
 * Implementations:
 * - [HttpOllamaChatClient] : real HTTP client (java.net.http.HttpClient)
 *
 * @see OcrConfig for provider/model/endpoint settings
 */
interface OllamaChatClient {

    /**
     * Sends a chat request to the Ollama vision model and returns the raw text response.
     *
     * @param config OCR configuration (provider, model, endpoint, temperature)
     * @param prompt text instruction for structuring the extracted content
     * @param images list of base64-encoded image data URIs to feed the vision model
     * @return raw model text response (expected AsciiDoc structured text)
     */
    fun chat(config: OcrConfig, prompt: String, images: List<String>): String
}