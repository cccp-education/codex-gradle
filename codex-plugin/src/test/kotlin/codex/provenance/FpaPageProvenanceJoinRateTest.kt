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
 * CDX-PAGE-PROVENANCE-1 — join rate measurement on the real FPA corpus.
 *
 * The cadrage rated the TOC ↔ chunk join as the main risk (titles are written
 * by a human in the TOC, extracted by OCR in the chunks — the canonical form
 * is the only bridge). This test measures the real rate on the acquired corpus
 * (`office/metiers/FPA/.../codex-out/chunks.json` + the root TOC) and pins a
 * floor, so a regression in `SectionTitleNormalizer` or in the resolver is
 * caught on the real data rather than on synthetic fixtures.
 *
 * Skips cleanly when the private corpus is absent (fresh clone, CI) — the
 * corpus is never committed (office/ = private derivative work).
 *
 * Reads only — never mutates the corpus (Loi de l'Économie d'Encre).
 */
@Tag("integration")
class FpaPageProvenanceJoinRateTest {

    private val chunksFile: File? = locate(
        "office/metiers/FPA/Devenir_Formateur_Professionnel_d_Adultes_FPA_II/codex-out/chunks.json"
    )
    private val tocFile: File? = locate(
        "office/metiers/FPA/Devenir_Formateur_Professionnel_d_Adultes_FPA_II/Devenir_Formateur_Professionnel_d_Adultes_FPA_II.adoc"
    )

    private fun locate(relative: String): File? {
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
    fun `real fpa corpus join rate holds above the cadrage floor`() {
        assumeTrue(
            chunksFile != null && tocFile != null,
            "FPA corpus absent (office/ not present) — skipping real join rate measurement"
        )

        val chunks = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(DocumentChunk.serializer()), chunksFile!!.readText())
        val sections = TocPageIndex.parse(tocFile!!)

        val provenance = PageProvenanceResolver.resolve(chunks, sections, qualityReport = null)
        val rate = PageProvenanceResolver.joinRate(provenance)

        println("[codex] FPA page-provenance join rate: ${"%.3f".format(rate)} " +
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
