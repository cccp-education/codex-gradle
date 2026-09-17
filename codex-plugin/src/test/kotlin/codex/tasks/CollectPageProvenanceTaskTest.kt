package codex.tasks

import codex.ocr.OcrQualityIssue
import codex.ocr.OcrQualityReason
import codex.ocr.OcrQualityReport
import codex.provenance.PageProvenanceReport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-PAGE-PROVENANCE-2 — `collectPageProvenance` thin wrapper.
 *
 * Proves the Gradle surface: input wiring, the `page-provenance.json` sidecar
 * contract, the optional acquisition report (degraded silent when absent) and
 * the empty-chunks edge. The resolver itself is unit-tested in
 * `codex.provenance.PageProvenanceResolverTest`.
 *
 * Baby-step TDD strict RED → GREEN → REFACTOR.
 */
class CollectPageProvenanceTaskTest {

    private fun chunkJson(vararg chunks: Pair<String, String>): String =
        Json { prettyPrint = true }.encodeToString(
            ListSerializer(DocumentChunk.serializer()),
            chunks.map { (id, path) ->
                DocumentChunk(
                    id = id, sourceDocument = "livre", sectionPath = path,
                    headingLevel = 2, content = "body", license = "PROPRIETARY",
                )
            }
        )

    private fun tocText(): String =
        """
        | Référence | Sujet / Titre de la section | Page | Fichier
        | 1.2.1.1 | Organiser le contenu du scénario | 40 | 040.pdf
        | 1.0.1 | Devenir Formateur Professionnel d'Adultes | 5, 6, 7, 8 | 005.pdf, 006.pdf, 007.pdf, 008.pdf
        """.trimIndent()

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("collectPageProvenance", CollectPageProvenanceTask::class.java).get()

    @Test
    fun `writes the page provenance sidecar for a resolved chunk`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply {
            writeText(chunkJson("chk-1" to "Chapitre 1 > Organiser le contenu du scénario"))
        }
        val toc = File(dir, "toc.adoc").apply { writeText(tocText()) }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(toc)
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        assertTrue(out.exists(), "the sidecar must be written")
        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertEquals(1, report.chunkCount)
        assertEquals(1, report.resolvedCount)
        assertEquals(listOf(40), report.chunks[0].pages)
    }

    @Test
    fun `resolves a multi-page section to all its pages`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply {
            writeText(chunkJson("chk-1" to "Devenir Formateur Professionnel d'Adultes"))
        }
        val toc = File(dir, "toc.adoc").apply { writeText(tocText()) }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(toc)
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertEquals(listOf(5, 6, 7, 8), report.chunks[0].pages)
    }

    @Test
    fun `attaches the acquisition doubt when the optional report is provided`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply {
            writeText(chunkJson("chk-1" to "Chapitre 1 > Organiser le contenu du scénario"))
        }
        val toc = File(dir, "toc.adoc").apply { writeText(tocText()) }
        val quality = File(dir, "ocr-quality-report.json").apply {
            writeText(
                Json.encodeToString(
                    OcrQualityReport.serializer(),
                    OcrQualityReport(
                        imagesScanned = 10,
                        issues = listOf(OcrQualityIssue("040", "040.pdf", OcrQualityReason.ILLISIBLE)),
                    )
                )
            )
        }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(toc)
        t.qualityReportFile.set(quality)
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertTrue(report.chunks[0].doubtful)
        assertEquals(OcrQualityReason.ILLISIBLE, report.chunks[0].doubts[0].reason)
    }

    @Test
    fun `degrades silently when the optional report is absent`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply {
            writeText(chunkJson("chk-1" to "Chapitre 1 > Organiser le contenu du scénario"))
        }
        val toc = File(dir, "toc.adoc").apply { writeText(tocText()) }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(toc)
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertFalse(report.chunks[0].doubtful)
        assertTrue(report.chunks[0].doubts.isEmpty())
    }

    @Test
    fun `missing toc degrades every chunk to empty pages but writes the sidecar`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply {
            writeText(chunkJson("chk-1" to "Chapitre 1 > Organiser le contenu du scénario"))
        }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(File(dir, "absent.adoc"))
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertEquals(1, report.chunkCount)
        assertEquals(0, report.resolvedCount)
        assertTrue(report.chunks[0].pages.isEmpty())
    }

    @Test
    fun `empty chunk list produces an empty report`(@TempDir dir: File) {
        val chunks = File(dir, "chunks.json").apply { writeText("[]") }
        val toc = File(dir, "toc.adoc").apply { writeText(tocText()) }
        val out = File(dir, "page-provenance.json")

        val t = task()
        t.chunksFile.set(chunks)
        t.tocFile.set(toc)
        t.pageProvenanceFile.set(out)
        t.collectPageProvenance()

        val report = Json { ignoreUnknownKeys = true }
            .decodeFromString(PageProvenanceReport.serializer(), out.readText())
        assertEquals(0, report.chunkCount)
        assertEquals(0.0, report.joinRate)
    }

    @Test
    fun `plugin wires collectPageProvenance with an explicit toc file default`(@TempDir dir: File) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectPageProvenance")
        assertNotNull(task, "the plugin must register collectPageProvenance")
        assertEquals("collect", task!!.group)
    }

    @Test
    fun `qualityReportFile is optional by default`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectPageProvenance") as CollectPageProvenanceTask
        assertFalse(task.qualityReportFile.isPresent, "the acquisition report is optional")
    }
}
