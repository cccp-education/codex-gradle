package codex.bdd

import codebase.store.DocumentChunk
import codebase.store.DoubtExposure
import codebase.store.DoubtMetadata
import codebase.store.DoubtfulChunk
import codebase.store.RagVectorStore
import codebase.store.RetrieveResult
import codex.tasks.CodexCompositeContextTask
import codex.tasks.CodexIngestTask
import io.cucumber.java8.En
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Cucumber steps for `codex_doubt_bridge.feature` (CDX-DOUBT-BRIDGE).
 *
 * Pure BDD — no pgvector, no Docker. Drives the two production seams
 * ([CodexIngestTask.ingestInto] and [CodexCompositeContextTask.searchDoubtAware]
 * / [CodexCompositeContextTask.buildDocsContent]) against recording in-memory
 * subclasses of the `open` [RagVectorStore] (pattern S-102/S-214).
 *
 * Step prefixes are "doubt bridge" / "ingested" / "Docs content" / "socle"
 * to avoid DuplicateStepDefinition with the other codex BDD suites (bug S-088).
 */
class DoubtBridgeSteps : En {

    private val ingestStore = RecordingIngestStore()
    private val retrieveStore = RecordingRetrieveStore()
    private val chunks = mutableListOf<DocumentChunk>()
    private val results = mutableListOf<RetrieveResult>()
    private var docsContent: String = ""
    private var excluded: Boolean = false
    private var compositeJson: String = ""
    private var vibecodingJson: String = ""

    private fun ingestTask(): CodexIngestTask =
        ProjectBuilder.builder().build()
            .tasks.register("collectIngest", CodexIngestTask::class.java).get()

    private fun contextTask(): CodexCompositeContextTask =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    init {

        Given("a doubt bridge scenario") {
            chunks.clear()
            results.clear()
            docsContent = ""
            excluded = false
            ingestStore.received = null
            retrieveStore.called = false
        }

        // ── Ingestion scenarios ──────────────────────────────────────────

        Given("a chunk with content {string} and marker {string}") { content: String, marker: String ->
            chunks += DocumentChunk(
                id = "chk-doubt", sourceDocument = "book", sectionPath = "Ch1",
                headingLevel = 1, content = "$content\n$marker\nmore", license = "PROPRIETARY",
            )
        }

        Given("a clean chunk with content {string}") { content: String ->
            chunks += DocumentChunk(
                id = "chk-clean", sourceDocument = "book", sectionPath = "Ch1",
                headingLevel = 1, content = content, license = "PROPRIETARY",
            )
        }

        When("the chunks are ingested through the doubt bridge") {
            runBlocking { ingestTask().ingestInto(ingestStore, chunks) }
        }

        Then("the ingested doubt marks the chunk doubtful") {
            assertTrue(ingestStore.received!!.single().doubt.doubtful)
        }

        Then("the ingested doubt does not mark the chunk doubtful") {
            assertFalse(ingestStore.received!!.single().doubt.doubtful)
        }

        Then("the ingested confidence is zero") {
            assertTrue(ingestStore.received!!.single().doubt.confidence == 0.0)
        }

        Then("the ingested confidence is full") {
            assertTrue(ingestStore.received!!.single().doubt.confidence == DoubtMetadata.MAX_CONFIDENCE)
        }

        // ── Docs channel scenarios ───────────────────────────────────────

        Given("a clean retrieved chunk {string}") { text: String ->
            results += retrieveResult(text, doubtful = false)
        }

        Given("a doubtful retrieved chunk {string}") { text: String ->
            results += retrieveResult(text, doubtful = true, confidence = 0.0)
        }

        When("the Docs channel is composed without exclusion") {
            excluded = false
            docsContent = contextTask().buildDocsContent(results, excludeDoubtful = false)
        }

        When("the Docs channel is composed with exclusion") {
            excluded = true
            docsContent = contextTask().buildDocsContent(results, excludeDoubtful = true)
        }

        Then("the Docs content contains {string}") { fragment: String ->
            assertTrue(docsContent.contains(fragment), "Docs content should contain '$fragment', was: $docsContent")
        }

        Then("the Docs content does not contain {string}") { fragment: String ->
            assertFalse(docsContent.contains(fragment), "Docs content should not contain '$fragment', was: $docsContent")
        }

        Then("the Docs content contains the doubt marker") {
            assertTrue(docsContent.contains(DoubtExposure.DOUBT_MARKER))
        }

        // ── Retrieval scenario ───────────────────────────────────────────

        When("the composite context retrieval runs") {
            runBlocking { contextTask().searchDoubtAware(retrieveStore, "query", 5) }
        }

        Then("the socle doubt-aware search was used") {
            assertTrue(retrieveStore.called, "searchWithDoubt must be used, not the doubt-blind search")
        }

        // ── N3 JSON contract scenarios (bug P0 S-220) ────────────────────

        When("the composite context JSON is built") {
            compositeJson = contextTask().buildCompositeJson(results, "query", 12)
        }

        Then("the composite JSON parses with {int} entries") { expected: Int ->
            val root = Json.parseToJsonElement(compositeJson).jsonObject
            assertEquals(expected, root["entries"]!!.jsonArray.size)
            assertEquals(expected.toString(), root["count"]!!.jsonPrimitive.content)
        }

        Then("the composite JSON exposes a doubtful entry") {
            val doubtful = Json.parseToJsonElement(compositeJson).jsonObject["entries"]!!
                .jsonArray.map { it.jsonObject }
                .filter { it["doubtful"]!!.jsonPrimitive.content == "true" }
            assertTrue(doubtful.isNotEmpty(), "a doubtful entry must be exposed with doubtful=true")
        }

        When("the vibecoding JSON is built") {
            docsContent = contextTask().buildDocsContent(results, excludeDoubtful = false)
            vibecodingJson = contextTask().buildVibecodingJson(docsContent, "query", 12, results.size)
        }

        Then("the vibecoding JSON exposes the docs section") {
            val root = Json.parseToJsonElement(vibecodingJson).jsonObject
            assertEquals(docsContent, root["docsSection"]!!.jsonPrimitive.content)
        }
    }

    private fun retrieveResult(text: String, doubtful: Boolean, confidence: Double = 1.0) =
        RetrieveResult(
            chunkId = 1L, chunkIndex = 0, chunkText = text, sectionPath = "Ch1",
            headingLevel = 1, sourceDocument = "book", similarity = 0.8,
            confidence = confidence, doubtful = doubtful,
        )

    private class RecordingIngestStore : RagVectorStore() {
        var received: List<DoubtfulChunk>? = null
        override suspend fun ingestWithDoubt(
            chunks: List<DoubtfulChunk>,
            batchLogger: (String) -> Unit,
        ): Int {
            received = chunks
            return chunks.map { it.chunk.sourceDocument }.distinct().size
        }
    }

    private class RecordingRetrieveStore : RagVectorStore() {
        var called = false
        override suspend fun searchWithDoubt(query: String, topK: Int): List<RetrieveResult> {
            called = true
            return emptyList()
        }
    }
}
