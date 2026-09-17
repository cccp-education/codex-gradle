package codex.tasks

import codebase.store.RetrieveResult
import codex.provenance.ChunkPageProvenance
import codex.provenance.PageProvenanceReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-PAGE-PROVENANCE-3 — additive `pages` field in the N3
 * `composite-context.json` contract.
 *
 * The page provenance sidecar (`page-provenance.json`, US-2) is joined back to
 * the retrieval results so each composite entry localises its source page. The
 * join key is `sectionPath` — **not** `chunkId`: the retrieval result's
 * `chunkId` is the pgvector `BIGSERIAL` id (`Long`), whereas the sidecar keys on
 * the semantic chunk id (SHA-256 `String`); the only shared identity is
 * `sourceDocument + sectionPath` (cadrage D6 deviation, documented S-224).
 *
 * Backward compatible: without a sidecar, the `pages` field is absent and the
 * existing JSON is byte-identical.
 *
 * Baby-step TDD strict RED → GREEN → REFACTOR.
 */
class CodexCompositeContextPageTest {

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    private fun result(sectionPath: String) = RetrieveResult(
        chunkId = 1L, chunkIndex = 0, chunkText = "body",
        sectionPath = sectionPath, headingLevel = 2, sourceDocument = "livre",
        similarity = 0.8, confidence = 1.0, doubtful = false,
    )

    private fun sidecar(dir: File, vararg entries: Pair<String, List<Int>>): File {
        val provenance = entries.map { (path, pages) ->
            ChunkPageProvenance(chunkId = "chk-x", sectionPath = path, pages = pages)
        }
        val file = File(dir, "page-provenance.json")
        file.writeText(
            Json.encodeToString(
                PageProvenanceReport.serializer(),
                PageProvenanceReport.of("livre", provenance),
            )
        )
        return file
    }

    @Test
    fun `composite entry exposes the pages joined by sectionPath`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "Chapitre 1 > Organiser le contenu du scénario" to listOf(40)))

        val raw = t.buildCompositeJson(
            listOf(result("Chapitre 1 > Organiser le contenu du scénario")), "q", 5, t.buildPageIndex()
        )

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("40"), entry["pages"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `composite entry exposes a multi-page list`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "Devenir Formateur Professionnel d'Adultes" to listOf(5, 6, 7, 8)))

        val raw = t.buildCompositeJson(listOf(result("Devenir Formateur Professionnel d'Adultes")), "q", 5, t.buildPageIndex())

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("5", "6", "7", "8"), entry["pages"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `pages field is omitted when the section is not in the sidecar`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "Chapitre 1 > Autre section" to listOf(40)))

        val raw = t.buildCompositeJson(listOf(result("Chapitre 1 > Inconnue")), "q", 5)

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertFalse(entry.containsKey("pages"), "an unresolved entry must not carry an empty pages field")
    }

    @Test
    fun `pages field is absent without a sidecar (backward compat)`() {
        val t = task()

        val raw = t.buildCompositeJson(listOf(result("Chapitre 1 > Organiser le contenu du scénario")), "q", 5)

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertFalse(entry.containsKey("pages"), "no sidecar → no pages field (byte-identical JSON)")
    }

    @Test
    fun `invalid sidecar degrades silently to no pages`(@TempDir dir: File) {
        val t = task()
        val bad = File(dir, "page-provenance.json").apply { writeText("{ not json") }
        t.pageProvenanceFile.setFrom(bad)

        val raw = t.buildCompositeJson(listOf(result("Chapitre 1 > Organiser le contenu du scénario")), "q", 5)

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertFalse(entry.containsKey("pages"))
    }

    @Test
    fun `doubtful and confidence fields survive the page enrichment`(@TempDir dir: File) {
        val t = task()
        t.pageProvenanceFile.setFrom(sidecar(dir, "Chapitre 1 > Organiser le contenu du scénario" to listOf(40)))

        val raw = t.buildCompositeJson(listOf(result("Chapitre 1 > Organiser le contenu du scénario")), "q", 5)

        val entry = Json.parseToJsonElement(raw).jsonObject["entries"]!!.jsonArray[0].jsonObject
        assertEquals("false", entry["doubtful"]!!.jsonPrimitive.content)
        assertTrue(entry.containsKey("confidence"))
        assertTrue(entry.containsKey("chunkId"))
    }

    @Test
    fun `plugin wires an optional pageProvenanceFile default`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("generateCompositeContext") as CodexCompositeContextTask
        // Tolerant collection: the artefact is targeted by default; its absence
        // degrades to no pages (pattern CDX-CONTEXT-HARDENING S-221).
        assertTrue(task.pageProvenanceFile.from.size >= 1, "the sidecar artefact is targeted by default")
    }
}
