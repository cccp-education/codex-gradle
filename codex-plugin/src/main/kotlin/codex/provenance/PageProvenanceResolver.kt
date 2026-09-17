package codex.provenance

import codex.ocr.OcrQualityReport
import codex.tasks.DocumentChunk

/**
 * Pure resolver that closes the acquisition chain: a doubtful chunk now knows
 * **from which page** it comes.
 *
 * The chunk knows it is doubtful but not from which page; the acquisition
 * report (`OcrQualityReport`, S-218) knows which page is doubtful but not which
 * chunks derive from it. The resolver joins the two halves with a **derived
 * join** — no DDL, no re-vectorisation, no embedding (Loi de l'Économie
 * d'Encre):
 *
 * 1. TOC (section → page) via [TocPageIndex.pageIndex];
 * 2. chunks (deepest `sectionPath` segment that matches, canonicalized by
 *    [ProvenanceTitleNormalizer] — canon S-217 + OCR refinements) → pages;
 * 3. acquisition report (page → doubt) attached to the resolved pages.
 *
 * The resolver is deterministic, side-effect free and degrades silently: a
 * missing TOC, an unresolved section, or a non-numeric issue page never throws
 * — the chunk simply carries no page and no doubt.
 */
object PageProvenanceResolver {

    /**
     * Resolves the page provenance of every chunk.
     *
     * @param chunks the semantic chunks produced by the chunker
     * @param tocSections the parsed TOC sections (source of truth for section → page)
     * @param qualityReport the optional acquisition report (page → doubt); `null` degrades to no doubt
     * @return one [ChunkPageProvenance] per input chunk, in input order
     */
    fun resolve(
        chunks: List<DocumentChunk>,
        tocSections: List<TocSection>,
        qualityReport: OcrQualityReport?,
    ): List<ChunkPageProvenance> {
        if (chunks.isEmpty()) return emptyList()
        val titleIndex = TocPageIndex.pageIndex(tocSections)
        val doubtByPage = doubtsByPage(qualityReport)
        return chunks.map { chunk ->
            val pages = pagesOf(chunk.sectionPath, titleIndex)
            val doubts = pages.mapNotNull { doubtByPage[it] }.flatten()
            ChunkPageProvenance(
                chunkId = chunk.id,
                sectionPath = chunk.sectionPath,
                pages = pages,
                doubts = doubts,
            )
        }
    }

    /**
     * Share of chunks whose section resolved to at least one page, in [0.0, 1.0].
     * `0.0` for an empty provenance (degraded silent). Used to measure the
     * TOC ↔ chunk join rate on the real corpus (cadrage risk).
     */
    fun joinRate(provenance: List<ChunkPageProvenance>): Double {
        if (provenance.isEmpty()) return 0.0
        return provenance.count { it.pages.isNotEmpty() }.toDouble() / provenance.size.toDouble()
    }

    private fun pagesOf(sectionPath: String, titleIndex: Map<String, List<Int>>): List<Int> {
        val trimmed = sectionPath.trim()
        if (trimmed.isEmpty()) return emptyList()
        // Deepest-first walk: the most precise matching segment (closest to the
        // leaf) wins, falling back to an ancestor when the leaf is OCR-polluted
        // beyond recognition. Measured on the real FPA corpus: leaf-only joins
        // 24.8% of chunks, the ancestor walk 70.8% (cadrage S-223 risk).
        val segments = trimmed.split('>').map { it.trim() }.filter { it.isNotEmpty() }
        for (segment in segments.asReversed()) {
            val key = ProvenanceTitleNormalizer.normalize(segment)
            if (key.isEmpty()) continue
            titleIndex[key]?.let { return it }
        }
        return emptyList()
    }

    private fun doubtsByPage(report: OcrQualityReport?): Map<Int, List<PageDoubt>> {
        if (report == null || report.issues.isEmpty()) return emptyMap()
        val byPage = linkedMapOf<Int, MutableList<PageDoubt>>()
        for (issue in report.issues) {
            val page = issue.pageId.trim().toIntOrNull() ?: continue
            if (page <= 0) continue
            byPage.getOrPut(page) { mutableListOf() } +=
                PageDoubt(page = page, reason = issue.reason, detail = issue.detail)
        }
        return byPage
    }
}
