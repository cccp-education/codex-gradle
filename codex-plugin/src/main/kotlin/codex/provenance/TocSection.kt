package codex.provenance

import kotlinx.serialization.Serializable

/**
 * A section entry from a book table of contents, reduced to the
 * (title → pages) mapping needed for chunk page provenance.
 *
 * Codex is READ: it consumes a TOC (AsciiDoc table
 * `Référence | Titre | Page | Fichier`, format `BookTocParser`) as an explicit
 * input, never hardcoded — the FPA business content lives in `office/`, never
 * in this public repo.
 *
 * @property ref hierarchical reference (e.g. `1.2.1.1`)
 * @property title the section title as written in the TOC
 * @property pages the physical page numbers this section spans (ordered,
 *   deduplicated), possibly several when a section spans multiple scans
 */
@Serializable
data class TocSection(
    val ref: String,
    val title: String,
    val pages: List<Int>,
) {
    init {
        require(ref.isNotBlank()) { "TocSection ref must not be blank" }
        require(title.isNotBlank()) { "TocSection title must not be blank" }
        require(pages.all { it > 0 }) { "TocSection pages must all be positive, got: $pages" }
    }
}
