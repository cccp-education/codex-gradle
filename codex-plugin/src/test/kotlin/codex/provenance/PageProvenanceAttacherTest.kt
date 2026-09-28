package codex.provenance

import codebase.store.DocumentChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — CB-PAGE-PROVENANCE-2 : `PageProvenanceAttacher` (pure domain).
 *
 * The sidecar `page-provenance.json` (N2, S-224) keys its chunks on the exact
 * SHA-256 `ChunkPageProvenance.chunkId`; the ingestion payload keys its chunks
 * on the exact `DocumentChunk.id` — the same value. The attacher joins the two
 * at ingestion time by identity, which is **exact** (unlike the retrieval join
 * by `sectionPath`, heuristic at 70.8% — dev. D6 S-224).
 *
 * The attacher is deterministic, side-effect free and degrades silently: a
 * missing report, an unmatched id or an empty page list leaves the chunk
 * untouched. Baby-step TDD RED -> GREEN -> REFACTOR.
 */
class PageProvenanceAttacherTest {

    private fun chunk(id: String, pages: List<Int> = emptyList()) = DocumentChunk(
        id = id, sourceDocument = "livre", sectionPath = "Chapitre 1 > Section",
        headingLevel = 2, content = "body", license = "PROPRIETARY", pages = pages,
    )

    private fun report(vararg entries: Pair<String, List<Int>>) = PageProvenanceReport.of(
        "livre",
        entries.map { (id, pages) ->
            ChunkPageProvenance(chunkId = id, sectionPath = "Chapitre 1 > Section", pages = pages)
        },
    )

    @Test
    fun `attaches the page of a chunk by exact sha-256 id`() {
        val attached = PageProvenanceAttacher.attach(listOf(chunk("chk-1")), report("chk-1" to listOf(40)))

        assertEquals(listOf(40), attached.single().pages)
    }

    @Test
    fun `preserves all pages of a multi-page section`() {
        val attached = PageProvenanceAttacher.attach(
            listOf(chunk("chk-1")),
            report("chk-1" to listOf(5, 6, 7, 8)),
        )

        assertEquals(listOf(5, 6, 7, 8), attached.single().pages)
    }

    @Test
    fun `leaves an unmatched chunk untouched`() {
        val attached = PageProvenanceAttacher.attach(
            listOf(chunk("chk-1"), chunk("chk-2")),
            report("chk-1" to listOf(40)),
        )

        assertEquals(listOf(40), attached[0].pages)
        assertTrue(attached[1].pages.isEmpty(), "a chunk absent from the sidecar keeps no page")
    }

    @Test
    fun `preserves pages already carried by a chunk absent from the sidecar`() {
        val attached = PageProvenanceAttacher.attach(
            listOf(chunk("chk-1", pages = listOf(9))),
            report("chk-other" to listOf(40)),
        )

        assertEquals(listOf(9), attached.single().pages)
    }

    @Test
    fun `a null report degrades to the original chunks`() {
        val chunks = listOf(chunk("chk-1"))

        val attached = PageProvenanceAttacher.attach(chunks, null)

        assertSame(chunks, attached, "a missing sidecar must not allocate a new list")
    }

    @Test
    fun `a report with no pages degrades to the original chunks`() {
        val chunks = listOf(chunk("chk-1"))

        val attached = PageProvenanceAttacher.attach(chunks, report("chk-1" to emptyList()))

        assertSame(chunks, attached, "an unresolved sidecar must not allocate a new list")
    }

    @Test
    fun `an empty chunk list degrades to the original`() {
        val chunks = emptyList<DocumentChunk>()

        val attached = PageProvenanceAttacher.attach(chunks, report("chk-1" to listOf(40)))

        assertSame(chunks, attached)
    }

    @Test
    fun `does not mutate the input chunk`() {
        val original = chunk("chk-1")

        PageProvenanceAttacher.attach(listOf(original), report("chk-1" to listOf(40)))

        assertTrue(original.pages.isEmpty(), "the input chunk must stay immutable")
    }
}
