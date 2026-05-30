package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.time.Instant

/**
 * OCR result contract — structured AsciiDoc output returned by the OCR pipeline.
 *
 * Produced by codex (Brooklyn), consumed by codebase (Queens) for RAG ingestion.
 *
 * @property structuredText extracted text in structured AsciiDoc format
 *                         (with section headers, code blocks, tables)
 * @property confidence OCR confidence score in [0.0, 1.0]
 * @property language detected or confirmed language code (ISO 639-1)
 * @property sourceFormat original image MIME type
 * @property generatedAt ISO-8601 timestamp of generation
 * @property model OCR/LLM model identifier used for extraction
 * @property metadata arbitrary key-value metadata (e.g. page number, document title)
 */
data class OcrResult(
    val structuredText: String,
    val confidence: Double,
    val language: String,
    val sourceFormat: String,
    val generatedAt: String,
    val model: String,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Deserializes an OCR result from a JSON file.
         *
         * @param file JSON file containing a serialized OcrResult
         * @return parsed [OcrResult] instance
         */
        fun fromFile(file: File): OcrResult =
            mapper.readValue(file, OcrResult::class.java)

        /**
         * Serializes this result as a pretty-printed JSON file.
         *
         * @param dir target directory (created if missing)
         * @param result the result to persist
         * @return the created [File] handle
         */
        fun writeTo(dir: File, result: OcrResult): File {
            dir.mkdirs()
            val file = File(dir, "ocr-result.json")
            file.writeText(mapper.writeValueAsString(result))
            return file
        }

        /**
         * Creates a minimal OcrResult for testing or scaffolding.
         *
         * @param text extracted structured AsciiDoc text
         * @param confidence OCR confidence (0.0-1.0)
         * @param language ISO 639-1 language code
         * @param model LLM/OCR model identifier
         * @param metadata extra key-value pairs
         * @return a new [OcrResult] with sensible defaults
         */
        fun of(
            text: String,
            confidence: Double,
            language: String,
            model: String,
            metadata: Map<String, String> = emptyMap()
        ): OcrResult = OcrResult(
            structuredText = text,
            confidence = confidence,
            language = language,
            sourceFormat = "image/png",
            generatedAt = Instant.now().toString(),
            model = model,
            metadata = metadata
        )
    }
}
