package codex.tasks

import codex.Metadata
import codex.store.CodexVectorStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CodexCompositeContextTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val query: Property<String>

    @get:Input
    @get:Optional
    abstract val topK: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val q = query.orNull ?: "architecture du workspace"
        val k = topK.orNull?.toIntOrNull() ?: 10

        val store = CodexVectorStore()
        val results = store.searchBlocking(q, k)

        val entries = results.map { r ->
            mapOf(
                "source" to "codex",
                "chunkId" to r.chunkId,
                "chunkText" to r.chunkText.take(500),
                "sectionPath" to r.sectionPath,
                "headingLevel" to r.headingLevel,
                "sourceDocument" to r.sourceDocument,
                "similarity" to r.similarity
            )
        }

        val composite = mapOf<String, Any>(
            "source" to "brooklyn",
            "query" to q,
            "topK" to k,
            "entries" to entries,
            "count" to entries.size
        )

        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        output.writeText(Json { prettyPrint = true }.encodeToString(composite))

        Metadata.writeTo(
            output.parentFile,
            Metadata.forBrooklyn(type = "composite-context", sessions = entries.size)
        )

        logger.lifecycle("[codex] generateCompositeContext — {} entries → {}", entries.size, output.absolutePath)
    }
}
