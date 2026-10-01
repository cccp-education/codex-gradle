package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.DoubtfulChunk
import codebase.store.RagVectorStore
import kotlinx.coroutines.runBlocking
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C-4 (S-233) — the ingestion flags TOC-polluted chunks doubtful.
 *
 * The N1 socle policy only sees the `[ILLISIBLE]` marker; a TOC entry rendered
 * as a dot-leader heading carries no such marker, so it would be ingested at
 * full confidence. `CodexIngestTask` overlays the acquisition-local
 * [TocNoiseDetector] so the noise rides the existing doubt transport — no
 * re-chunking, no re-vectorisation, no N1 change.
 *
 * Pinned on the `internal ingestInto` seam through a recording `open`
 * [RagVectorStore] (pattern S-214/S-232, no Docker).
 */
class CodexIngestTocNoiseTest {

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

    private fun chunks(vararg chunks: DocumentChunk) = chunks.toList()

    @Test
    fun `ingestInto flags a toc-polluted chunk doubtful`() {
        val store = RecordingRagStore()
        val toc = DocumentChunk(
            id = "chk-toc", sourceDocument = "livre", sectionPath = "Book > TOC",
            headingLevel = 2, content = "## Merci : ......................3", license = "PROPRIETARY",
        )

        runBlocking { task().ingestInto(store, chunks(toc)) }

        assertTrue(store.received!!.single().doubt.doubtful, "a TOC chunk must be doubtful")
        assertTrue(store.received!!.single().doubt.confidence == TocNoiseDetector.TOC_NOISE_CONFIDENCE)
    }

    @Test
    fun `ingestInto keeps a clean chunk at full confidence`() {
        val store = RecordingRagStore()
        val clean = DocumentChunk(
            id = "chk-clean", sourceDocument = "livre", sectionPath = "Book > Intro",
            headingLevel = 2, content = "## Introduction\nSubstantive readable body.", license = "PROPRIETARY",
        )

        runBlocking { task().ingestInto(store, chunks(clean)) }

        assertFalse(store.received!!.single().doubt.doubtful)
    }

    @Test
    fun `ingestInto still honours the illisible marker`() {
        val store = RecordingRagStore()
        val illisible = DocumentChunk(
            id = "chk-doubt", sourceDocument = "livre", sectionPath = "Book > Ch2",
            headingLevel = 1, content = "Text\n[ILLISIBLE]\nmore", license = "PROPRIETARY",
        )

        runBlocking { task().ingestInto(store, chunks(illisible)) }

        assertTrue(store.received!!.single().doubt.doubtful)
        assertTrue(store.received!!.single().doubt.confidence == 0.0)
    }
}
