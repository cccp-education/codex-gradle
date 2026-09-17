package codex.provenance

import codex.ocr.OcrQualityIssue
import codex.ocr.OcrQualityReason
import codex.ocr.OcrQualityReport
import codebase.store.DocumentChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — CDX-PAGE-PROVENANCE-1 : `PageProvenanceResolver`.
 *
 * The chunk knows it is doubtful but not *from which page* it comes; the
 * acquisition report knows which page is doubtful but not which chunks derive
 * from it. The resolver closes the gap with a **derived join** (zéro DDL, zéro
 * re-vectorisation — Loi de l'Économie d'Encre): TOC (section → page) × chunks
 * (`sectionPath`) × acquisition report (page → doubt), joined by the normalized
 * last `sectionPath` segment ({@code SectionTitleNormalizer}, canon S-217
 * 115/115).
 *
 * Degraded silent: no TOC → empty pages, unresolved page → empty pages, no
 * report → empty doubts. Never fails.
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class PageProvenanceResolverTest {

    private fun chunk(id: String, sectionPath: String) = DocumentChunk(
        id = id,
        sourceDocument = "livre",
        sectionPath = sectionPath,
        headingLevel = 2,
        content = "body",
        license = "PROPRIETARY",
    )

    private fun toc(ref: String, title: String, pages: List<Int>) =
        TocSection(ref = ref, title = title, pages = pages)

    private fun report(vararg issues: OcrQualityIssue) =
        OcrQualityReport(imagesScanned = 10, issues = issues.toList())

    @Test
    fun `joins a section whose leaf is OCR-polluted by walking to the matching ancestor`() {
        // Real FPA shape: the chunks sectionPath nests many polluted segments;
        // the leaf is unrecognizable, but an ancestor matches the TOC.
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(
                chunk(
                    "chk-1",
                    "Devenir Formateur Professionnel d'Adultes > FPA II > " +
                        "Historique du Titre Professionnel : ...13 > Organiser le contenu du scénario ....40"
                )
            ),
            tocSections = listOf(
                toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40)),
                toc("1.0.2", "Historique du Titre Professionnel : Évolution des REAC (v1 à v7)", listOf(14)),
            ),
            qualityReport = null,
        )

        assertEquals(listOf(40), provenance[0].pages)
    }

    @Test
    fun `deepest matching ancestor wins over a broader ancestor`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(
                chunk(
                    "chk-1",
                    "Devenir Formateur Professionnel d'Adultes > " +
                        "Les compétences. 14 > Le numérique. 15"
                )
            ),
            tocSections = listOf(
                toc("1.0.1", "Devenir Formateur Professionnel d'Adultes", listOf(5, 6, 7, 8)),
                toc("1.0.2.1", "Les compétences", listOf(14)),
                toc("1.0.2.2", "Le numérique", listOf(15)),
            ),
            qualityReport = null,
        )

        assertEquals(listOf(15), provenance[0].pages)
    }

    @Test
    fun `resolves a chunk to the page of its normalized last section segment`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertEquals(1, provenance.size)
        assertEquals("chk-1", provenance[0].chunkId)
        assertEquals(listOf(40), provenance[0].pages)
    }

    @Test
    fun `resolves a multi-page section to all its pages`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Devenir Formateur Professionnel d'Adultes")),
            tocSections = listOf(toc("1.0.1", "Devenir Formateur Professionnel d'Adultes", listOf(5, 6, 7, 8))),
            qualityReport = null,
        )

        assertEquals(listOf(5, 6, 7, 8), provenance[0].pages)
    }

    @Test
    fun `joins despite polluted titles on either side`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Élaborer un SPG..........26")),
            tocSections = listOf(toc("1.1.3", "Élaborer un Scénario Pédagogique Global (SPG)", listOf(26))),
            qualityReport = null,
        )

        // Dot leaders stripped, but the abbreviations differ — the join is
        // exact on the canonical form, so this pair is intentionally unresolved.
        assertTrue(provenance[0].pages.isEmpty())
    }

    @Test
    fun `joins on the canonical form of a dot-leader polluted pair`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario .............")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario&#8230;&#8203;", listOf(40))),
            qualityReport = null,
        )

        assertEquals(listOf(40), provenance[0].pages)
    }

    @Test
    fun `unresolved section degrades to empty pages but stays listed`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Section absente du TOC")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertEquals(1, provenance.size)
        assertTrue(provenance[0].pages.isEmpty())
    }

    @Test
    fun `missing toc degrades every chunk to empty pages`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Section")),
            tocSections = emptyList(),
            qualityReport = null,
        )

        assertEquals(1, provenance.size)
        assertTrue(provenance[0].pages.isEmpty())
    }

    @Test
    fun `blank sectionPath degrades to empty pages`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "   ")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertTrue(provenance[0].pages.isEmpty())
    }

    @Test
    fun `empty chunk list yields empty provenance`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = emptyList(),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertTrue(provenance.isEmpty())
    }

    @Test
    fun `attaches the acquisition doubt when the issue page matches`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = report(
                OcrQualityIssue("040", "040.pdf", OcrQualityReason.ILLISIBLE)
            ),
        )

        val p = provenance[0]
        assertTrue(p.doubtful)
        assertEquals(1, p.doubts.size)
        assertEquals(40, p.doubts[0].page)
        assertEquals(OcrQualityReason.ILLISIBLE, p.doubts[0].reason)
    }

    @Test
    fun `doubt is attached to any of the resolved multi-pages`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Devenir Formateur Professionnel d'Adultes")),
            tocSections = listOf(toc("1.0.1", "Devenir Formateur Professionnel d'Adultes", listOf(5, 6, 7, 8))),
            qualityReport = report(
                OcrQualityIssue("007", "007.pdf", OcrQualityReason.LOW_CONFIDENCE, detail = "0.31")
            ),
        )

        val p = provenance[0]
        assertTrue(p.doubtful)
        assertEquals(listOf(7), p.doubts.map { it.page })
        assertEquals("0.31", p.doubts[0].detail)
    }

    @Test
    fun `doubt on an unrelated page is not attached`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = report(
                OcrQualityIssue("099", "099.pdf", OcrQualityReason.IMAGE_MISSING, detail = "fig.png")
            ),
        )

        assertFalse(provenance[0].doubtful)
        assertTrue(provenance[0].doubts.isEmpty())
    }

    @Test
    fun `non-numeric issue pageId is ignored (degraded silent)`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = report(
                OcrQualityIssue("scan_040_hd", "scan_040_hd.png", OcrQualityReason.ILLISIBLE)
            ),
        )

        assertFalse(provenance[0].doubtful)
    }

    @Test
    fun `missing quality report degrades to no doubt`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertFalse(provenance[0].doubtful)
        assertTrue(provenance[0].doubts.isEmpty())
    }

    @Test
    fun `doubt is scoped to the chunks that resolve to the doubtful page`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(
                chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario"),
                chunk("chk-2", "Chapitre 2 > Autre section"),
            ),
            tocSections = listOf(
                toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40)),
                toc("2.0.1", "Autre section", listOf(73)),
            ),
            qualityReport = report(
                OcrQualityIssue("040", "040.pdf", OcrQualityReason.ILLISIBLE)
            ),
        )

        assertTrue(provenance[0].doubtful)
        assertFalse(provenance[1].doubtful)
    }

    @Test
    fun `carries the chunk sectionPath for downstream joining`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario")),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertEquals("Chapitre 1 > Organiser le contenu du scénario", provenance[0].sectionPath)
    }

    @Test
    fun `resolution is deterministic and idempotent`() {
        val chunks = listOf(chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario"))
        val tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40)))

        val once = PageProvenanceResolver.resolve(chunks, tocSections, null)
        val twice = PageProvenanceResolver.resolve(chunks, tocSections, null)

        assertEquals(once, twice)
    }

    @Test
    fun `join rate summarizes the resolved share`() {
        val provenance = PageProvenanceResolver.resolve(
            chunks = listOf(
                chunk("chk-1", "Chapitre 1 > Organiser le contenu du scénario"),
                chunk("chk-2", "Chapitre 2 > Section absente du TOC"),
            ),
            tocSections = listOf(toc("1.2.1.1", "Organiser le contenu du scénario", listOf(40))),
            qualityReport = null,
        )

        assertEquals(0.5, PageProvenanceResolver.joinRate(provenance))
    }

    @Test
    fun `join rate over an empty provenance is zero`() {
        assertEquals(0.0, PageProvenanceResolver.joinRate(emptyList()))
    }
}
