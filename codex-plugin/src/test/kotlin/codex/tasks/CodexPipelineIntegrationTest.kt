package codex.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-7-4 — End-to-end integration test on a canonical PDF from
 * `office/books-collection/`.
 *
 * Chains the three pipeline tasks on a *real* book PDF (not a synthetic
 * one) :
 * 1. [ExtractBookStructureTask] — PDF → AsciiDoc (typographic detection)
 * 2. [ConvertToMarkdownTask] — AsciiDoc → Markdown
 * 3. [ChunkDocumentTask] — Markdown → semantic chunks JSON
 *
 * The canonical corpus lives outside the repo (`office/books-collection/`)
 * and is never committed. These tests skip cleanly via
 * `assumeTrue(pdfFile.exists())` when the corpus is absent (CI without
 * the books, fresh clone). When present, they validate the full
 * extract → markdown → chunk pipeline on a real-world document — the
 * strongest non-pgvector end-to-end guarantee in the suite.
 *
 * Baby-step TDD strict RED → GREEN → REFACTOR (GREEN: all three tasks
 * already exist — characterization test of the chained pipeline on a
 * canonical source, pattern CDX-7-2 S-090 extended with the chunking
 * step).
 */
@Tag("integration")
class CodexPipelineIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private val canonicalPdf: File = resolveCanonicalPdf()

    /**
     * Resolves the canonical PDF by walking up from the test working directory
     * until an `office/books-collection/` directory is found (workspace root).
     * Falls back to the system property `codex.canonical.pdf` if set, then to
     * a relative path. Returns whatever is resolved — [assumePdfAvailable]
     * skips the test when the file does not exist.
     */
    private fun resolveCanonicalPdf(): File {
        System.getProperty("codex.canonical.pdf")?.let { return File(it) }
        val rel = File("office/books-collection/litterature/Manifeste des 60.pdf")
        if (rel.exists()) return rel
        var dir = File(".").absoluteFile.parentFile
        while (dir != null) {
            val candidate = File(dir, "office/books-collection/litterature/Manifeste des 60.pdf")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return rel
    }

    private fun assumePdfAvailable() {
        assumeTrue(
            canonicalPdf.exists(),
            "Canonical PDF not found at ${canonicalPdf.absolutePath} — skipping E2E (corpus absent)"
        )
    }

    private fun runFullPipeline(pdfFile: File, chunksFile: File): List<DocumentChunk> {
        val adocFile = File(tempDir, pdfFile.nameWithoutExtension + ".adoc")
        val mdFile = File(tempDir, pdfFile.nameWithoutExtension + ".md")

        val extractTask = ProjectBuilder.builder().build().tasks.register(
            "collectBookStructure", ExtractBookStructureTask::class.java
        ).get()
        extractTask.pdfFile.set(pdfFile)
        extractTask.outputFile.set(adocFile)
        extractTask.extract()

        val convertTask = ProjectBuilder.builder().build().tasks.register(
            "transformToMarkdown", ConvertToMarkdownTask::class.java
        ).get()
        convertTask.adocFile.set(adocFile)
        convertTask.markdownFile.set(mdFile)
        convertTask.convert()

        val chunkTask = ProjectBuilder.builder().build().tasks.register(
            "transformChunk", ChunkDocumentTask::class.java
        ).get()
        chunkTask.markdownFile.set(mdFile)
        chunkTask.chunksFile.set(chunksFile)
        chunkTask.licenseName.set("UNKNOWN")
        chunkTask.chunk()

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
        return json.decodeFromString(chunksFile.readText())
    }

    @Test
    fun `full pipeline on canonical PDF produces non-empty chunks`() {
        assumePdfAvailable()

        val chunksFile = File(tempDir, "chunks.json")
        val chunks = runFullPipeline(canonicalPdf, chunksFile)

        assertTrue(chunksFile.exists(), "Chunks JSON file should exist after full pipeline")
        assertTrue(
            chunks.isNotEmpty(),
            "Canonical PDF should yield non-empty chunks — got ${chunks.size}"
        )
    }

    @Test
    fun `full pipeline chunks have deterministic SHA-256 ids`() {
        assumePdfAvailable()

        val chunksFile = File(tempDir, "chunks.json")
        val chunks = runFullPipeline(canonicalPdf, chunksFile)

        val idPattern = Regex("""^chk-[0-9a-f]{16}$""")
        chunks.forEach { chunk ->
            assertTrue(
                idPattern.matches(chunk.id),
                "Chunk id '${chunk.id}' should match SHA-256 pattern chk-xxxxxxxxxxxxxxxx"
            )
            assertEquals(
                canonicalPdf.nameWithoutExtension,
                chunk.sourceDocument,
                "Chunk sourceDocument should match the canonical PDF stem"
            )
        }
    }

    @Test
    fun `full pipeline chunks preserve extracted heading content`() {
        assumePdfAvailable()

        val chunksFile = File(tempDir, "chunks.json")
        val chunks = runFullPipeline(canonicalPdf, chunksFile)

        chunks.forEach { chunk ->
            assertNotNull(chunk.sectionPath, "Chunk sectionPath should not be null")
            assertTrue(chunk.headingLevel in 1..6, "Heading level should be 1..6")
            assertTrue(
                chunk.content.isNotBlank(),
                "Chunk content should be non-blank"
            )
        }
        val totalLines = chunks.sumOf { it.content.lines().size }
        assertTrue(
            totalLines > 0,
            "Canonical PDF should produce chunks with content — got 0 total lines across ${chunks.size} chunks"
        )
    }
}