package cccp.education.codex

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.time.Instant

data class Metadata(
    val source: String,
    val type: String,
    val sessions: Int,
    val generatedAt: String,
    val model: String,
    val version: String,
    val dependencies: List<String>
) {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        fun writeTo(dir: File, metadata: Metadata): File {
            dir.mkdirs()
            val file = File(dir, "metadata.json")
            file.writeText(mapper.writeValueAsString(metadata))
            return file
        }

        fun forBrooklyn(
            type: String = "pipeline",
            model: String = "onnx-local",
            sessions: Int = 0,
            dependencies: List<String> = listOf("queens")
        ): Metadata = Metadata(
            source = "brooklyn",
            type = type,
            sessions = sessions,
            generatedAt = Instant.now().toString(),
            model = model,
            version = "1.0",
            dependencies = dependencies
        )
    }
}
