package codex.bdd

import codebase.store.RetrieveResult
import codebase.store.DoubtfulChunk
import codebase.store.RagVectorStore
import codex.ocr.OcrQualityIssue
import codex.ocr.OcrQualityReason
import codex.ocr.OcrQualityReport
import codex.provenance.ChunkPageProvenance
import codex.provenance.PageProvenanceReport
import codex.provenance.PageProvenanceResolver
import codex.provenance.TocSection
import codex.tasks.CodexCompositeContextTask
import codex.tasks.CodexIngestTask
import codebase.store.DocumentChunk
import io.cucumber.java8.En
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * Cucumber steps for `codex_page_provenance.feature` (CDX-PAGE-PROVENANCE-4).
 *
 * Pure BDD — the derived join (TOC × chunks × acquisition report) and the
 * additive N3 `pages` contract are driven through the production objects
 * ([PageProvenanceResolver], [CodexCompositeContextTask.buildCompositeJson]),
 * with no pgvector, no Docker, no filesystem.
 *
 * Step prefixes are "page provenance" / "chunk" / "toc section" / "acquisition
 * doubt" / "page" to avoid DuplicateStepDefinition with the other codex BDD
 * suites (bug S-088).
 */
class PageProvenanceSteps : En {

    private val chunks = mutableListOf<DocumentChunk>()
    private val tocSections = mutableListOf<TocSection>()
    private val issues = mutableListOf<OcrQualityIssue>()
    private var provenance: List<ChunkPageProvenance> = emptyList()
    private var compositeJson: String = ""
    private var retrieval: RetrieveResult? = null

    // CDX-PAGE-PROVENANCE-2 — ingestion-side transport.
    private var sidecarFile: File? = null
    private var ingestedChunks: List<DocumentChunk> = emptyList()

    private fun task(): CodexCompositeContextTask =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    private fun ingestTask(): CodexIngestTask =
        ProjectBuilder.builder().build()
            .tasks.register("collectIngest", CodexIngestTask::class.java).get()

    init {

        Given("a page provenance scenario") {
            chunks.clear()
            tocSections.clear()
            issues.clear()
            provenance = emptyList()
            compositeJson = ""
            retrieval = null
            sidecarFile = null
            ingestedChunks = emptyList()
        }

        Given("a chunk {string} in section {string}") { id: String, sectionPath: String ->
            chunks += DocumentChunk(
                id = id, sourceDocument = "livre", sectionPath = sectionPath,
                headingLevel = 2, content = "body", license = "PROPRIETARY",
            )
        }

        Given("a toc section {string} titled {string} on page {int}") { ref: String, title: String, page: Int ->
            tocSections += TocSection(ref, title, listOf(page))
        }

        Given("a toc section {string} titled {string} on pages {int}, {int}, {int}, {int}") {
                ref: String, title: String, a: Int, b: Int, c: Int, d: Int ->
            tocSections += TocSection(ref, title, listOf(a, b, c, d))
        }

        Given("an acquisition doubt on page {int} with reason {string}") { page: Int, reason: String ->
            issues += OcrQualityIssue(
                pageId = page.toString(), imageFile = "image.png",
                reason = OcrQualityReason.valueOf(reason),
            )
        }

        Given("a retrieval result in section {string}") { sectionPath: String ->
            retrieval = RetrieveResult(
                chunkId = 1L, chunkIndex = 0, chunkText = "body", sectionPath = sectionPath,
                headingLevel = 2, sourceDocument = "livre", similarity = 0.8,
                confidence = 1.0, doubtful = false,
            )
        }

        When("the page provenance is resolved") {
            val report = if (issues.isEmpty()) null else OcrQualityReport(imagesScanned = 1, issues = issues.toList())
            provenance = PageProvenanceResolver.resolve(chunks, tocSections, report)
        }

        When("the composite context JSON is built with the page provenance") {
            val resolverInput = provenance.ifEmpty {
                PageProvenanceResolver.resolve(chunks, tocSections, null)
            }
            val report = PageProvenanceReport.of("livre", resolverInput)
            val index = report.chunks
                .filter { it.pages.isNotEmpty() }
                .associate { it.sectionPath to it.pages }
            compositeJson = task().buildCompositeJson(listOf(retrieval!!), "q", 5, index)
        }

        Then("the chunk {string} is localised on page {int}") { id: String, page: Int ->
            assertEquals(listOf(page), chunk(id).pages)
        }

        Then("the chunk {string} is localised on pages {int}, {int}, {int}, {int}") {
                id: String, a: Int, b: Int, c: Int, d: Int ->
            assertEquals(listOf(a, b, c, d), chunk(id).pages)
        }

        Then("the chunk {string} is not localised") { id: String ->
            assertTrue(chunk(id).pages.isEmpty(), "chunk $id should carry no page")
        }

        Then("the chunk {string} is doubtful") { id: String ->
            assertTrue(chunk(id).doubtful, "chunk $id should be doubtful")
        }

        Then("the chunk {string} is not doubtful") { id: String ->
            assertFalse(chunk(id).doubtful, "chunk $id should not be doubtful")
        }

        Then("the chunk {string} doubt reason on page {int} is {string}") { id: String, page: Int, reason: String ->
            val doubt = chunk(id).doubts.first { it.page == page }
            assertEquals(OcrQualityReason.valueOf(reason), doubt.reason)
        }

        Then("the page provenance join rate is {int}") { rate: Int ->
            assertEquals(rate.toDouble(), PageProvenanceResolver.joinRate(provenance))
        }

        Then("the composite entry exposes page {int}") { page: Int ->
            val entry = Json.parseToJsonElement(compositeJson).jsonObject["entries"]!!
                .jsonArray[0].jsonObject
            assertEquals(
                listOf(page.toString()),
                entry["pages"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }

        // ── CDX-PAGE-PROVENANCE-2 — N2 ingestion transport ───────────────

        Given("a page provenance sidecar resolving chunk {string} to page {int}") { id: String, page: Int ->
            val report = PageProvenanceReport.of(
                "livre",
                listOf(ChunkPageProvenance(chunkId = id, sectionPath = "Chapitre 1 > Section", pages = listOf(page))),
            )
            sidecarFile = File.createTempFile("page-provenance-", ".json").apply {
                deleteOnExit()
                writeText(Json.encodeToString(report))
            }
        }

        When("the chunks are ingested with the page provenance") {
            val task = ingestTask()
            sidecarFile?.let { task.pageProvenanceFile.setFrom(it) }
            val store = RecordingIngestStore()
            runBlocking { task.ingestInto(store, task.attachPages(chunks.toList())) }
            ingestedChunks = store.received!!.map { it.chunk }
        }

        Then("the ingested chunk {string} carries page {int}") { id: String, page: Int ->
            assertEquals(listOf(page), ingestedChunks.single { it.id == id }.pages)
        }

        Then("the ingested chunk {string} carries no page") { id: String ->
            assertTrue(ingestedChunks.single { it.id == id }.pages.isEmpty())
        }
    }

    /** Recording in-memory store — no pgvector, no Docker (pattern S-102/S-214). */
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

    private fun chunk(id: String): ChunkPageProvenance =
        provenance.first { it.chunkId == id }
}
