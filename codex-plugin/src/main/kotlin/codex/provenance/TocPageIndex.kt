package codex.provenance

import java.io.File

/**
 * Pure parser + index for a book table of contents (AsciiDoc table
 * `Référence | Titre | Page | Fichier`, format document-gradle `BookTocParser`).
 *
 * Mirrors the TOC semantics owned by document-gradle (the pair borough, N2
 * WRITE + PUBLISH) without a compile dependency: codex reads the *file*
 * contract, never the `document` package (parallel N2 boroughs).
 *
 * The TOC is the source of truth for the section → page mapping. Parsing is
 * pure and deterministic — no file is mutated (Loi de l'Économie d'Encre).
 */
object TocPageIndex {

    private val REF_PATTERN = Regex("""^\d+(\.\d+)*$""")

    /**
     * Parses [tocFile] into an ordered list of [TocSection]s.
     *
     * @return the sections (one per TOC row, pages expanded inline); empty if
     *   the file is missing, empty, or contains no valid data row.
     */
    fun parse(tocFile: File): List<TocSection> {
        if (!tocFile.exists() || !tocFile.isFile) return emptyList()
        return parse(tocFile.readText())
    }

    /**
     * Parses the TOC [content] (AsciiDoc table body) into [TocSection]s.
     *
     * Header rows, non-table lines, malformed references, blank titles, rows
     * with fewer than four cells and rows without a numeric page are skipped
     * (degraded silent — an unpolluted TOC never fails).
     */
    fun parse(content: String): List<TocSection> {
        val sections = mutableListOf<TocSection>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith("|")) continue
            val cells = line.split("|").drop(1).map { it.trim() }
            if (cells.size < 4) continue

            val ref = cells[0]
            val title = cells[1]
            if (!REF_PATTERN.matches(ref)) continue
            if (title.isBlank()) continue

            val pages = cells[2].split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }
                .distinct()
            if (pages.isEmpty()) continue

            sections += TocSection(ref = ref, title = title, pages = pages)
        }
        return sections
    }

    /**
     * Builds the canonical-title → pages index used by the resolver join.
     *
     * Titles are canonicalized with [ProvenanceTitleNormalizer] (composes the
     * proven [SectionTitleNormalizer] canon S-217 with OCR refinements —
     * leading numeric references, trailing punctuation/leaders). Titles
     * normalizing to empty are dropped; duplicate titles union their pages in
     * order.
     */
    fun pageIndex(sections: List<TocSection>): Map<String, List<Int>> {
        val index = linkedMapOf<String, MutableList<Int>>()
        for (section in sections) {
            val key = ProvenanceTitleNormalizer.normalize(section.title)
            if (key.isEmpty()) continue
            val pages = index.getOrPut(key) { mutableListOf() }
            section.pages.forEach { page -> if (page !in pages) pages += page }
        }
        return index.mapValues { (_, pages) -> pages.toList() }
    }
}
