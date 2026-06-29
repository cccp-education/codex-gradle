package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

/**
 * OCR pipeline configuration — provider and model settings for the OCR task.
 *
 * Shared contract between codex (Brooklyn) and codebase (Queens).
 * The config file lives in the project directory (e.g. `ocr-config.json`)
 * and is read at Gradle task execution time.
 *
 * @property provider LLM/OCR provider identifier (e.g. "ollama", "openai")
 * @property model concrete model name at the provider (e.g. "gpt-oss:120b-cloud")
 * @property maxTokens maximum output tokens for the LLM call
 * @property temperature sampling temperature [0.0, 2.0]
 * @property endpoint optional custom API endpoint (defaults to provider's standard)
 * @property extra arbitrary provider-specific key-value overrides
 */
data class OcrConfig(
    val provider: String,
    val model: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.0,
    val endpoint: String? = null,
    val extra: Map<String, String> = emptyMap()
) {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Default config filename used by the OCR task.
         */
        const val DEFAULT_FILENAME = "ocr-config.json"

        /**
         * Loads config from a JSON file.
         *
         * @param file JSON file path
         * @return parsed [OcrConfig]
         */
        fun fromFile(file: File): OcrConfig =
            mapper.readValue(file, OcrConfig::class.java)

        /**
         * Serializes this config as a pretty-printed JSON file.
         *
         * @param dir target directory (created if missing)
         * @param config the config to persist
         * @return the created [File] handle
         */
        fun writeTo(dir: File, config: OcrConfig): File {
            dir.mkdirs()
            val file = File(dir, DEFAULT_FILENAME)
            file.writeText(mapper.writeValueAsString(config))
            return file
        }

        /**
         * Returns the default OCR config for local Ollama.
         *
         * @return [OcrConfig] pointing at gpt-oss:120b-cloud with temperature 0
         */
        fun defaultConfig(): OcrConfig = OcrConfig(
            provider = "ollama",
            model = "gpt-oss:120b-cloud",
            maxTokens = 4096,
            temperature = 0.0,
            endpoint = null,
            extra = emptyMap()
        )
    }
}
