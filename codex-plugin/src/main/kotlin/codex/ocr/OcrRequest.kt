package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

/**
 * OCR request contract — payload sent from codebase (Queens) to codex (Brooklyn)
 * for extracting structured AsciiDoc from image data.
 *
 * This type lives in codex-gradle (Brooklyn) as the authoritative contract.
 * Codebase-gradle (Queens) consumes it as a dependency via MavenLocal.
 *
 * @property imageData raw bytes of the image to OCR (PNG, JPEG, TIFF)
 * @property format image MIME type (e.g. "image/png", "image/jpeg", "image/tiff")
 * @property language ISO 639-1 language code hint (e.g. "fr", "en", "de")
 * @property prompt optional LLM prompt override for structuring instructions
 */
data class OcrRequest(
    val imageData: ByteArray,
    val format: String,
    val language: String,
    val prompt: String? = null
) {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Deserializes an OCR request from a JSON file.
         *
         * @param file JSON file containing a serialized OcrRequest
         * @return parsed [OcrRequest] instance
         */
        fun fromFile(file: File): OcrRequest =
            mapper.readValue(file, OcrRequest::class.java)

        /**
         * Serializes this request as a pretty-printed JSON file.
         *
         * @param dir target directory (created if missing)
         * @param request the request to persist
         * @return the created [File] handle
         */
        fun writeTo(dir: File, request: OcrRequest): File {
            dir.mkdirs()
            val file = File(dir, "ocr-request.json")
            file.writeText(mapper.writeValueAsString(request))
            return file
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OcrRequest) return false
        return imageData.contentEquals(other.imageData) &&
                format == other.format &&
                language == other.language &&
                prompt == other.prompt
    }

    override fun hashCode(): Int {
        var result = imageData.contentHashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (prompt?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OcrRequest(format='$format', language='$language', imageSize=${imageData.size} bytes, prompt=$prompt)"
}
