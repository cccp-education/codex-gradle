package codex.tasks

import codebase.store.DoubtMetadata
import codebase.store.DoubtfulChunk
import codebase.store.RagVectorStore
import kotlinx.coroutines.runBlocking
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CDX-DOUBT-BRIDGE-1 — the ingestion task must not reimplement doubt.
 *
 * Before this US, [CodexIngestTask] orchestrated its own R2DBC loop with
 * `StoreStatements.insertChunk()` (5 binds, no `confidence`/`doubtful`):
 * a chunk derived from an `[ILLISIBLE]` page was ingested exactly like a
 * clean one, so the augmented context could cite shaky OCR text unknowingly.
 *
 * The N1 socle already owns doubt (`DoubtPolicy`, `ingestWithDoubt`). Codex
 * becomes a true thin wrapper: it marks the chunks with the socle policy and
 * delegates the ingestion — a single ingestion implementation lives in N1.
 *
 * This suite pins the delegation seam without Docker through a recording
 * subclass of the `open` [RagVectorStore] (pattern S-102/S-214).
 *
 * Baby-step TDD RED -> GREEN -> REFACTOR.
 */
class CodexIngestDoubtTest {

    /** Recording in-memory subclass — no pgvector, no R2DBC, no Docker. */
    private class RecordingRagStore : RagVectorStore() {
        var received: List<DoubtfulChunk>? = null

        override suspend fun ingestWithDoubt(
            chunks: List<DoubtfulChunk>,
            batchLogger: (String) -> Unit,
        ): Int {
            received = chunks
            return chunks.map { it.chunk.sourceDocument }.distinct().size
        }
    }

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("collectIngest", CodexIngestTask::class.java).get()

    @Test
    fun `ingestInto delegates to the N1 socle with doubt-marked chunks`() {
        val store = RecordingRagStore()
        val chunks = listOf(
            codebase.store.DocumentChunk(
                id = "chk-clean", sourceDocument = "book", sectionPath = "Ch1",
                headingLevel = 1, content = "A clean readable section.", license = "PROPRIETARY",
            ),
            codebase.store.DocumentChunk(
                id = "chk-doubt", sourceDocument = "book", sectionPath = "Ch2",
                headingLevel = 1, content = "Text\n[ILLISIBLE]\nmore", license = "PROPRIETARY",
            ),
        )

        val docCount = runBlocking { task().ingestInto(store, chunks) }

        assertTrue(store.received != null, "the socle ingestWithDoubt must be called")
        assertEquals(1, docCount, "the socle document count must be returned")
    }

    @Test
    fun `ingestInto marks an illisible chunk doubtful with zero confidence`() {
        val store = RecordingRagStore()
        val chunks = listOf(
            codebase.store.DocumentChunk(
                id = "chk-doubt", sourceDocument = "book", sectionPath = "Ch2",
                headingLevel = 1, content = "Text\n[ILLISIBLE]\nmore", license = "PROPRIETARY",
            ),
        )

        runBlocking { task().ingestInto(store, chunks) }

        val doubt = store.received!!.single().doubt
        assertEquals(true, doubt.doubtful, "an [ILLISIBLE] chunk must be flagged doubtful")
        assertEquals(0.0, doubt.confidence, "a doubtful chunk carries zero confidence")
    }

    @Test
    fun `ingestInto keeps a clean chunk at full confidence`() {
        val store = RecordingRagStore()
        val chunks = listOf(
            codebase.store.DocumentChunk(
                id = "chk-clean", sourceDocument = "book", sectionPath = "Ch1",
                headingLevel = 1, content = "A clean readable section.", license = "PROPRIETARY",
            ),
        )

        runBlocking { task().ingestInto(store, chunks) }

        val doubt = store.received!!.single().doubt
        assertEquals(false, doubt.doubtful, "a clean chunk must not be flagged doubtful")
        assertEquals(DoubtMetadata.MAX_CONFIDENCE, doubt.confidence)
    }

    @Test
    fun `ingestInto detects the doubt marker in the section path too`() {
        val store = RecordingRagStore()
        val chunks = listOf(
            codebase.store.DocumentChunk(
                id = "chk-path", sourceDocument = "book", sectionPath = "Ch1 > [ILLISIBLE]",
                headingLevel = 2, content = "Body that is long enough to be above the short threshold.",
                license = "PROPRIETARY",
            ),
        )

        runBlocking { task().ingestInto(store, chunks) }

        assertTrue(store.received!!.single().doubt.doubtful, "the marker in sectionPath must flag doubt")
    }
}
