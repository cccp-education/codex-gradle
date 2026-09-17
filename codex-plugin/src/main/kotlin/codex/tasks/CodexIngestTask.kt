package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.DoubtPolicy
import codebase.store.RagVectorStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Vectorizes document chunks with ONNX AllMiniLmL6V2 and stores them in
 * pgvector, doubt-aware.
 *
 * EPIC CDX-RAG-3 (thin wrapper) + CDX-DOUBT-BRIDGE-1 (doubt delegation) :
 * the task is a true thin wrapper over the N1 socle
 * [codebase.store.RagVectorStore]. It does NOT reimplement the ingestion
 * loop nor the doubt policy — both live in codebase:
 * - [DoubtPolicy] derives the doubt from the OCR `[ILLISIBLE]` markers
 *   (content / sectionPath / overlapNext);
 * - [RagVectorStore.ingestWithDoubt] owns the additive schema, the 7-bind
 *   INSERT and the per-document `avg_confidence`.
 *
 * A chunk derived from an `[ILLISIBLE]` page is now ingested flagged
 * `doubtful` with zero confidence, so the augmented context can weight or
 * exclude it instead of citing shaky OCR text unknowingly. Tables
 * `codex_documents` / `codex_chunks` are unchanged (additive columns only,
 * zero re-vectorisation — Loi de l'Économie d'Encre).
 *
 * @property chunksFile input JSON chunks file
 * @property pgHost PostgreSQL host
 * @property pgPort PostgreSQL port
 * @property pgDatabase PostgreSQL database name
 * @property pgUser PostgreSQL username
 * @property pgPassword PostgreSQL password
 * @property batchSize number of chunks per batch (default: 32)
 */
@DisableCachingByDefault(because = "ONNX embeddings + pgvector (R2DBC) — external dependencies, non-cacheable")
abstract class CodexIngestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chunksFile: RegularFileProperty
    @get:Input abstract val pgHost: Property<String>
    @get:Input abstract val pgPort: Property<String>
    @get:Input abstract val pgDatabase: Property<String>
    @get:Input abstract val pgUser: Property<String>
    @get:Input abstract val pgPassword: Property<String>
    @get:Optional @get:Input abstract val batchSize: Property<String>

    @TaskAction
    fun ingest() = runBlocking {
        val input = chunksFile.asFile.get()
        val host = pgHost.get(); val port = pgPort.get().toInt()
        val db = pgDatabase.get(); val user = pgUser.get(); val pass = pgPassword.get()

        logger.lifecycle("[codex] collectIngest : ${input.name} → pgvector ($host:$port/$db)")

        val json = Json { ignoreUnknownKeys = true }
        val chunks = json.decodeFromString<List<DocumentChunk>>(input.readText())

        val store = RagVectorStore(
            host = host,
            port = port,
            database = db,
            username = user,
            password = pass,
        )

        val docCount = ingestInto(store, chunks)
        logger.lifecycle("[codex] ✓ collectIngest — $docCount docs, ${chunks.size} chunks (doubt-aware)")
    }

    /**
     * Delegates the doubt-aware ingestion to the N1 socle.
     *
     * Extracted as an `internal` seam so the delegation can be verified with
     * a recording fake [RagVectorStore] (no Docker) — the real R2DBC path is
     * covered by the testcontainers integration suite.
     *
     * @param store the N1 socle store (open for test substitution)
     * @param chunks the chunks to ingest
     * @return the number of documents ingested (socle count)
     */
    internal suspend fun ingestInto(store: RagVectorStore, chunks: List<DocumentChunk>): Int {
        val effectiveBatchSize = batchSize.orNull?.toIntOrNull() ?: 32
        return store.ingestWithDoubt(DoubtPolicy.mark(chunks)) { doc ->
            logger.lifecycle("[codex]   $doc (markers=doubt-aware, batch=$effectiveBatchSize)")
        }
    }
}
