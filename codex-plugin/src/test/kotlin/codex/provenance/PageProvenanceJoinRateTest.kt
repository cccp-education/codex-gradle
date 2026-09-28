package codex.provenance

import codebase.store.DocumentChunk
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

/**
 * CDX-PAGE-PROVENANCE-1 — join rate measurement on a real acquired corpus.
 *
 * The cadrage rated the TOC ↔ chunk join as the main risk (titles are written
 * by a human in the TOC, extracted by OCR in the chunks — the canonical form
 * is the only bridge). This test measures the real rate on an acquired corpus
 * and pins a floor, so a regression in `SectionTitleNormalizer` or in the
 * resolver is caught on real data rather than on synthetic fixtures.
 *
 * The corpus is private: its location is supplied by the caller through the
 * system properties `codex.pageProvenance.chunks` (a `chunks.json`) and
 * `codex.pageProvenance.toc` (the book TOC `.adoc`). The public repo never
 * hardcodes the private business content. The test skips cleanly when the
 * properties are absent (fresh clone, CI).
 *
 * Reads only — never mutates the corpus (Loi de l'Économie d'Encre).
 */
@Tag("integration")
class PageProvenanceJoinRateTest {

    private val chunksFile: File? = locate(System.getProperty("codex.pageProvenance.chunks"))
    private val tocFile: File? = locate(System.getProperty("codex.pageProvenance.toc"))

    private fun locate(relative: String?): File? {
        if (relative.isNullOrBlank()) return null
        val direct = File(relative)
        if (direct.exists()) return direct
        var dir = File(".").absoluteFile.parentFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun `real corpus join rate holds above the cadrage floor`() {
        assumeTrue(
            chunksFile != null && tocFile != null,
            "corpus absent (set codex.pageProvenance.chunks/toc) — skipping real join rate measurement"
        )

        val chunks = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(DocumentChunk.serializer()), chunksFile!!.readText())
        val sections = TocPageIndex.parse(tocFile!!)

        val provenance = PageProvenanceResolver.resolve(chunks, sections, qualityReport = null)
        val rate = PageProvenanceResolver.joinRate(provenance)

        println("[codex] page-provenance join rate: ${"%.3f".format(rate)} " +
            "(${provenance.count { it.pages.isNotEmpty() }}/${provenance.size} chunks, " +
            "${sections.size} TOC sections)")

        assertTrue(chunks.isNotEmpty(), "the real corpus must yield chunks")
        assertTrue(sections.isNotEmpty(), "the real TOC must yield sections")
        assertTrue(
            rate >= 0.5,
            "join rate must hold above the cadrage floor 0.5 — got ${"%.3f".format(rate)}"
        )
    }
}
