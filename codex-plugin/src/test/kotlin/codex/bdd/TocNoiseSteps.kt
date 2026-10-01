package codex.bdd

import codebase.store.DocumentChunk
import codebase.store.DoubtfulChunk
import codebase.store.RagVectorStore
import codex.tasks.CodexIngestTask
import codex.tasks.TocNoiseDetector
import io.cucumber.java8.En
import kotlinx.coroutines.runBlocking
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Cucumber steps for `codex_toc_noise.feature` (C-4, S-233).
 *
 * Pure BDD — no pgvector, no Docker. Drives the production ingestion seam
 * [CodexIngestTask.ingestInto] against a recording in-memory subclass of the
 * `open` [RagVectorStore] (pattern S-102/S-214), and pins the pure
 * [TocNoiseDetector] precision/recall.
 *
 * Step prefixes are "toc noise" to avoid DuplicateStepDefinition with the
 * other codex BDD suites (bug S-088).
 */
class TocNoiseSteps : En {

    private val store = RecordingRagStore()
    private val chunks = mutableListOf<DocumentChunk>()
    private var titleIsTocNoise: Boolean = false

    private fun ingestTask(): CodexIngestTask =
        ProjectBuilder.builder().build()
            .tasks.register("collectIngest", CodexIngestTask::class.java).get()

    init {

        Given("a toc noise scenario") {
            chunks.clear()
            store.received = null
        }

        Given("a toc noise chunk {string}") { content: String ->
            chunks += chunk(content)
        }

        Given("a toc noise chunk {string} and marker {string}") { content: String, marker: String ->
            chunks += chunk("$content\n$marker")
        }

        Given("a toc noise title {string}") { line: String ->
            titleIsTocNoise = TocNoiseDetector.isTocHeading(line)
        }

        When("the chunks are ingested through the toc noise bridge") {
            runBlocking { ingestTask().ingestInto(store, chunks) }
        }

        Then("the toc noise ingested doubt is doubtful") {
            assertTrue(store.received!!.single().doubt.doubtful)
        }

        Then("the toc noise ingested doubt is not doubtful") {
            assertFalse(store.received!!.single().doubt.doubtful)
        }

        Then("the toc noise ingested confidence is zero") {
            assertTrue(store.received!!.single().doubt.confidence == TocNoiseDetector.TOC_NOISE_CONFIDENCE)
        }

        Then("the toc noise ingested confidence is full") {
            assertTrue(store.received!!.single().doubt.confidence == codebase.store.DoubtMetadata.MAX_CONFIDENCE)
        }

        Then("the toc noise title is a table-of-contents heading") {
            assertTrue(titleIsTocNoise)
        }

        Then("the toc noise title is not a table-of-contents heading") {
            assertFalse(titleIsTocNoise)
        }
    }

    private fun chunk(content: String) = DocumentChunk(
        id = "chk-toc", sourceDocument = "livre", sectionPath = "Book > Section",
        headingLevel = 2, content = content, license = "PROPRIETARY",
    )

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
}
