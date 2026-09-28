package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.DoubtPolicy
import codebase.store.RagVectorStore
import codex.provenance.PageProvenanceAttacher
import codex.provenance.PageProvenanceReport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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
 * EPIC CB-PAGE-PROVENANCE US-2 (N2 transport) : the task also transports the
 * page provenance. When the optional sidecar `page-provenance.json`
 * ([PageProvenanceReport], S-224) is present, each chunk gains the page(s) its
 * exact SHA-256 id resolves to — the join is **exact**
 * (`ChunkPageProvenance.chunkId` == `DocumentChunk.id`) unlike the heuristic
 * retrieval join by `sectionPath` (70.8%, dev. D6 S-224). The page then rides
 * the N1 chunk (`DocumentChunk.pages`) and is exposed at retrieval.
 *
 * @property chunksFile input JSON chunks file
 * @property pageProvenanceFile optional page provenance sidecar
 *   (`page-provenance.json`); tolerant collection (pattern S-221) — absence
 *   degrades to no page (Économie d'Encre, never force `collectPageProvenance`)
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

    /**
     * Optional page provenance sidecar (`page-provenance.json`, a
     * [PageProvenanceReport]).
     *
     * `@InputFiles` tolerant (pattern CDX-CONTEXT-HARDENING S-221): the
     * artefact is targeted by default, its absence degrades to no page
     * (backward compatible, Économie d'Encre — never force
     * `collectPageProvenance`).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pageProvenanceFile: ConfigurableFileCollection

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
        val pageChunks = attachPages(chunks)

        val store = RagVectorStore(
            host = host,
            port = port,
            database = db,
            username = user,
            password = pass,
        )

        val docCount = ingestInto(store, pageChunks)
        logger.lifecycle("[codex] ✓ collectIngest — $docCount docs, ${chunks.size} chunks (doubt-aware, page-aware)")
    }

    /**
     * Attaches the page provenance to the ingestion chunks (EPIC
     * CB-PAGE-PROVENANCE US-2).
     *
     * Reads the optional `page-provenance.json` sidecar and joins it to the
     * chunks by exact SHA-256 id. Extracted as an `internal` seam so the
     * tolerant wiring can be verified without Docker; the join itself lives in
     * the pure [PageProvenanceAttacher].
     *
     * Degrades silently: absent/empty/invalid sidecar → the original chunks
     * (doubt-only ingestion, backward compatible).
     *
     * @param chunks the chunks decoded from `chunks.json`
     * @return the chunks carrying their resolved pages
     */
    internal fun attachPages(chunks: List<DocumentChunk>): List<DocumentChunk> =
        PageProvenanceAttacher.attach(chunks, readPageProvenance())

    /**
     * Reads the optional page provenance sidecar, degrading silently (null)
     * when it is absent or unreadable — the page transport must never fail
     * the build (pattern [CodexCompositeContextTask.buildPageIndex]).
     */
    private fun readPageProvenance(): PageProvenanceReport? {
        val file = pageProvenanceFile.singleOrNull() ?: return null
        if (!file.exists()) return null
        return try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString(PageProvenanceReport.serializer(), file.readText())
        } catch (e: Exception) {
            logger.warn("[codex] page provenance unreadable ({}), degrading to no page", e.message)
            null
        }
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
