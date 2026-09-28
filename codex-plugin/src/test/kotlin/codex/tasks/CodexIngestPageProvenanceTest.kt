package codex.tasks

import codebase.store.DocumentChunk
import codex.provenance.ChunkPageProvenance
import codex.provenance.PageProvenanceReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-PAGE-PROVENANCE-2 — the ingestion task transports the page.
 *
 * `CodexIngestTask` becomes the N2 bridge of the page metadata: it reads the
 * optional `page-provenance.json` sidecar (S-224) and attaches each chunk its
 * page(s) **before** delegating to the N1 socle — the exact SHA-256 join
 * (`ChunkPageProvenance.chunkId` == `DocumentChunk.id`), not the heuristic
 * retrieval join by `sectionPath`.
 *
 * The join is verified through the `internal attachPages` seam (mirror of
 * `CodexCompositeContextTask.buildPageIndex`): the pure attacher is unit-tested
 * in `codex.provenance.PageProvenanceAttacherTest`; the tolerant input wiring
 * is pinned here. Baby-step TDD RED -> GREEN -> REFACTOR.
 */
class CodexIngestPageProvenanceTest {

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("collectIngest", CodexIngestTask::class.java).get()

    private fun chunk(id: String) = DocumentChunk(
        id = id, sourceDocument = "livre", sectionPath = "Chapitre 1 > Section",
        headingLevel = 2, content = "body", license = "PROPRIETARY",
    )

    private fun sidecar(dir: File, vararg entries: Pair<String, List<Int>>): File =
        File(dir, "page-provenance.json").apply {
            val report = PageProvenanceReport.of(
                "livre",
                entries.map { (id, pages) ->
                    ChunkPageProvenance(chunkId = id, sectionPath = "Chapitre 1 > Section", pages = pages)
                },
            )
            writeText(Json { prettyPrint = true }.encodeToString(report))
        }

    @Test
    fun `attachPages resolves the page of a chunk from the sidecar`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "chk-1" to listOf(40)))

        val attached = t.attachPages(listOf(chunk("chk-1")))

        assertEquals(listOf(40), attached.single().pages)
    }

    @Test
    fun `attachPages preserves all pages of a multi-page section`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "chk-1" to listOf(5, 6, 7, 8)))

        val attached = t.attachPages(listOf(chunk("chk-1")))

        assertEquals(listOf(5, 6, 7, 8), attached.single().pages)
    }

    @Test
    fun `attachPages degrades silently when the sidecar is absent`() {
        val t = task()

        val attached = t.attachPages(listOf(chunk("chk-1")))

        assertTrue(attached.single().pages.isEmpty(), "an absent sidecar must leave the chunk page-less")
    }

    @Test
    fun `attachPages leaves an unmatched chunk untouched`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "chk-other" to listOf(40)))

        val attached = t.attachPages(listOf(chunk("chk-1")))

        assertTrue(attached.single().pages.isEmpty())
    }

    @Test
    fun `attachPages degrades silently on an unreadable sidecar`(@TempDir dir: File) {
        val t = task()
        val corrupt = File(dir, "page-provenance.json").apply { writeText("{ not json") }
        t.pageProvenanceFile.setFrom(corrupt)

        val attached = t.attachPages(listOf(chunk("chk-1")))

        assertTrue(attached.single().pages.isEmpty(), "a corrupt sidecar must degrade, never throw")
    }

    @Test
    fun `plugin wires the page provenance sidecar on the ingest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val ingest = project.tasks.findByName("collectIngest") as CodexIngestTask

        assertEquals(
            "page-provenance.json",
            ingest.pageProvenanceFile.files.singleOrNull()?.name,
            "the plugin must target the page provenance sidecar by default",
        )
    }
}
