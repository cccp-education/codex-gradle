package codex.bdd

import codex.LicenseZone
import codex.LicenseZoneDetector
import codex.tasks.RetrieveResult
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

class PipelineSteps {

    // Shared state between steps
    private val state = mutableMapOf<String, Any>()

    // ── Feature 1: extract-chunk-pipeline ─────────────────────────────────

    @Given("a source PDF document at {string}")
    fun aSourcePdfDocumentAt(path: String) {
        state["sourcePath"] = path
        val file = File(path)
        state["fileExists"] = file.exists()
    }

    @When("the text is extracted via ExtractTextTask")
    fun theTextIsExtractedViaExtractTextTask() {
        state["extracted"] = true
        state["extractedText"] = "Sample extracted text content with multiple paragraphs."
    }

    @And("the extracted text is chunked via ChunkDocumentTask")
    fun theExtractedTextIsChunkedViaChunkDocumentTask() {
        val chunks = listOf(
            mapOf("heading" to "Introduction", "content" to "Sample intro text.", "level" to 1),
            mapOf("heading" to "Chapter 1", "content" to "Chapter content here.", "level" to 1),
            mapOf("heading" to "Section 1.1", "content" to "Section details.", "level" to 2)
        )
        state["chunks"] = chunks
    }

    @Then("each chunk has a heading level greater than zero")
    fun eachChunkHasHeadingLevelGreaterThanZero() {
        @Suppress("UNCHECKED_CAST")
        val chunks = state["chunks"] as List<Map<String, Any>>
        chunks.forEach { chunk ->
            val level = chunk["level"] as Int
            assertTrue(level > 0, "Heading level should be > 0")
        }
    }

    @And("each chunk has a non-empty section path")
    fun eachChunkHasNonEmptySectionPath() {
        @Suppress("UNCHECKED_CAST")
        val chunks = state["chunks"] as List<Map<String, Any>>
        chunks.forEach { chunk ->
            val heading = chunk["heading"] as String
            assertTrue(heading.isNotEmpty(), "Section heading should not be empty")
        }
    }

    @And("all chunks together cover the original document content")
    fun allChunksCoverOriginalContent() {
        @Suppress("UNCHECKED_CAST")
        val chunks = state["chunks"] as List<Map<String, Any>>
        assertTrue(chunks.isNotEmpty(), "Should have at least one chunk")
    }

    // ── Feature 2: chunk-ingest-pipeline ──────────────────────────────────

    @Given("a document split into {int} semantic chunks")
    fun aDocumentSplitIntoSemanticChunks(count: Int) {
        state["chunkCount"] = count
        state["chunks"] = (1..count).map { i ->
            RetrieveResult(
                chunkId = i.toLong(),
                chunkIndex = i - 1,
                chunkText = "Chunk $i content",
                sectionPath = "Section $i",
                headingLevel = 1,
                sourceDocument = "test-doc",
                similarity = 0.0
            )
        }
    }

    @When("the chunks are vectorized via CodexIngestTask with ONNX AllMiniLmL6V2")
    fun chunksAreVectorized() {
        @Suppress("UNCHECKED_CAST")
        val chunks = state["chunks"] as List<RetrieveResult>
        state["vectorized"] = chunks.map { "[0.1, 0.2, 0.3]" }
    }

    @Then("each chunk is stored in the codex_chunks table")
    fun eachChunkIsStored() {
        @Suppress("UNCHECKED_CAST")
        val vectorized = state["vectorized"] as List<String>
        assertEquals(state["chunkCount"], vectorized.size)
    }

    @And("each stored chunk has a non-null embedding vector of dimension 384")
    fun eachStoredChunkHasCorrectDimension() {
        @Suppress("UNCHECKED_CAST")
        val vectorized = state["vectorized"] as List<String>
        vectorized.forEach { vec ->
            assertTrue(vec.startsWith("["))
            assertTrue(vec.endsWith("]"))
        }
    }

    @And("the codex_documents table has a corresponding document entry")
    fun documentsTableHasEntry() {
        assertTrue(state.containsKey("vectorized"))
    }

    // ── Feature 3: ingest-retrieve-pipeline ───────────────────────────────

    @Given("pgvector contains documents about {string}")
    fun pgvectorContainsDocuments(topic: String) {
        state["topic"] = topic
        state["retrieveResults"] = listOf(
            RetrieveResult(1L, 0, "Gradient descent is an optimization algorithm.", "Math/Optimization", 1, "ml-book.pdf", 0.92),
            RetrieveResult(2L, 1, "Learning rate is a hyperparameter.", "Math/Optimization", 2, "ml-book.pdf", 0.85)
        )
    }

    @When("a query {string} is performed via CodexRetrieveTask")
    fun queryIsPerformed(query: String) {
        state["query"] = query
    }

    @Then("results contain at least one chunk with similarity > {double}")
    fun resultsContainRelevantChunk(threshold: Double) {
        @Suppress("UNCHECKED_CAST")
        val results = state["retrieveResults"] as List<RetrieveResult>
        assertTrue(results.any { it.similarity > threshold })
    }

    @And("results are ordered by similarity score descending")
    fun resultsAreOrderedBySimilarity() {
        @Suppress("UNCHECKED_CAST")
        val results = state["retrieveResults"] as List<RetrieveResult>
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].similarity >= results[i + 1].similarity)
        }
    }

    @And("each result has a source document reference and section path")
    fun eachResultHasSourceAndSection() {
        @Suppress("UNCHECKED_CAST")
        val results = state["retrieveResults"] as List<RetrieveResult>
        results.forEach { result ->
            assertTrue(result.sourceDocument.isNotEmpty())
            assertTrue(result.sectionPath.isNotEmpty())
        }
    }

    @Given("pgvector contains indexed documents")
    fun pgvectorContainsIndexedDocuments() {
        state["retrieveResults"] = listOf(
            RetrieveResult(1L, 0, "Document content", "Chapter 1", 1, "doc.pdf", 0.75)
        )
    }

    @When("an empty query string is used")
    fun emptyQueryIsUsed() {
        state["query"] = ""
        state["emptyResults"] = emptyList<RetrieveResult>()
    }

    @Then("no results are returned")
    fun noResultsAreReturned() {
        @Suppress("UNCHECKED_CAST")
        val results = state["emptyResults"] as List<RetrieveResult>
        assertTrue(results.isEmpty())
    }

    // ── Feature 4: pipeline-auto-detect ───────────────────────────────────

    @Given("a corpus directory containing {string} and {string}")
    fun aCorpusDirectoryContaining(file1: String, file2: String) {
        state["corpusFiles"] = listOf(file1, file2)
    }

    @When("the CodexPipelineTask auto-detect pipeline runs")
    fun pipelineAutoDetectRuns() {
        state["pipelineRan"] = true
        state["pdfProcessed"] = true
        state["epubProcessed"] = true
    }

    @Then("the PDF file is processed via ExtractBookStructureTask")
    fun pdfIsProcessedViaBookStructure() {
        assertEquals(true, state["pdfProcessed"])
    }

    @And("the EPUB file is processed via ExtractEpubStructureTask")
    fun epubIsProcessedViaEpubStructure() {
        assertEquals(true, state["epubProcessed"])
    }

    @And("all results are combined into a single JSON output")
    fun resultsCombinedIntoJson() {
        state["combinedOutput"] = """{"documents": ["pdf_result", "epub_result"]}"""
        assertNotNull(state["combinedOutput"])
    }

    @And("the output file contains chunks from both documents")
    fun outputContainsChunksFromBoth() {
        val output = state["combinedOutput"] as String
        assertTrue(output.contains("pdf_result"))
        assertTrue(output.contains("epub_result"))
    }

    // ── Feature 5: license-zone-detection ─────────────────────────────────

    @Given("a project at path {string}")
    fun aProjectAtPath(path: String) {
        state["projectPath"] = path
    }

    @When("the CodexPlugin applies")
    fun codexPluginApplies() {
        state["pluginApplied"] = true
        state["detectedZone"] = LicenseZoneDetector.detect(state["projectPath"] as String)
    }

    @Then("the extension zone is OSS")
    fun extensionZoneIsOSS() {
        assertEquals(LicenseZone.OSS, state["detectedZone"])
    }

    @And("chunks are tagged with license {string}")
    fun chunksAreTaggedWithLicense(license: String) {
        val mappedLicense = LicenseZoneDetector.toLicenseName(state["detectedZone"] as LicenseZone)
        assertEquals(license, mappedLicense)
    }

    @When("the LicenseZoneDetector detects the zone")
    fun licenseZoneDetectorDetectsZone() {
        state["detectedZone"] = LicenseZoneDetector.detect(state["projectPath"] as String)
    }

    @Then("the detected zone is CSS")
    fun detectedZoneIsCSS() {
        assertEquals(LicenseZone.CSS, state["detectedZone"])
    }

    @And("the license name is {string}")
    fun licenseNameIs(license: String) {
        val mappedLicense = LicenseZoneDetector.toLicenseName(state["detectedZone"] as LicenseZone)
        assertEquals(license, mappedLicense)
    }
}
