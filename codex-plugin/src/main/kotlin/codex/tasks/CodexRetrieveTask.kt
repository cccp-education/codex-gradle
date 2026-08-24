package codex.tasks

import codebase.store.RagVectorStore
import codebase.store.RetrieveResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Performs semantic search against pgvector using cosine similarity.
 *
 * EPIC CDX-RAG-3 : thin wrapper delegating to the N1 socle
 * [codebase.store.RagVectorStore]. The store owns the ONNX embedding model
 * and R2DBC pgvector connection; this task only wires Gradle properties
 * (query, topK, pg config) and writes the JSON output.
 *
 * @property query the search query text
 * @property topK number of results to return (default: 10)
 * @property pgHost PostgreSQL host
 * @property pgPort PostgreSQL port
 * @property pgDatabase PostgreSQL database name
 * @property pgUser PostgreSQL username
 * @property pgPassword PostgreSQL password
 * @property outputFile JSON output file
 */
@DisableCachingByDefault(because = "ONNX pgvector search (R2DBC) — external dependencies, non-cacheable")
abstract class CodexRetrieveTask : DefaultTask() {

    @get:Input
    abstract val query: Property<String>

    @get:Input
    abstract val topK: Property<String>

    @get:Input
    abstract val pgHost: Property<String>

    @get:Input
    abstract val pgPort: Property<String>

    @get:Input
    abstract val pgDatabase: Property<String>

    @get:Input
    abstract val pgUser: Property<String>

    @get:Input
    abstract val pgPassword: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun retrieve() {
        val q = query.get()
        val k = topK.get().toInt()

        logger.lifecycle("[codex] collectRetrieve : \"${q.take(80)}\" → pgvector (top-$k)")

        val store = RagVectorStore(
            host = pgHost.get(),
            port = pgPort.get().toInt(),
            database = pgDatabase.get(),
            username = pgUser.get(),
            password = pgPassword.get()
        )
        val results: List<RetrieveResult> = store.searchBlocking(q, k)

        val json = Json { prettyPrint = true }
        outputFile.asFile.get().writeText(json.encodeToString(results))

        logger.lifecycle("[codex] ✓ collectRetrieve done — ${results.size} results returned")
    }
}