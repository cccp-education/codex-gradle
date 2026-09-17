package codex.provenance

import codex.ocr.OcrQualityReason
import kotlinx.serialization.Serializable

/**
 * A single acquisition-time doubt localised to a page, in the shape needed by
 * the provenance sidecar (subset of `OcrQualityIssue`, without the image file
 * name — the page number is the join key).
 *
 * @property page the physical page number the doubt was recorded on
 * @property reason the doubt reason (ILLISIBLE / TOO_SHORT / LOW_CONFIDENCE / IMAGE_MISSING)
 * @property detail discriminating evidence (confidence value, missing image path); `null` when self-evident
 */
@Serializable
data class PageDoubt(
    val page: Int,
    val reason: OcrQualityReason,
    val detail: String? = null,
) {
    init {
        require(page > 0) { "PageDoubt page must be positive, got: $page" }
    }
}

/**
 * Derived page provenance of a single chunk — the answer to "from which page
 * does this doubt come?".
 *
 * Computed by [PageProvenanceResolver] as a **derived join** (zéro DDL, zéro
 * re-vectorisation): the chunk keeps its `chunkId` (stable, SHA-256), gains the
 * page(s) its section maps to in the TOC, and inherits the acquisition doubt
 * recorded on any of those pages.
 *
 * @property chunkId stable chunk identifier (join key against the RAG retrieval)
 * @property sectionPath the chunk section path (preserved for citation/debug)
 * @property pages the physical pages the chunk section spans; empty when unresolved
 * @property doubts the acquisition doubts attached to the resolved pages (ordered)
 * @property doubtful true when at least one resolved page carries an acquisition doubt
 */
@Serializable
data class ChunkPageProvenance(
    val chunkId: String,
    val sectionPath: String,
    val pages: List<Int> = emptyList(),
    val doubts: List<PageDoubt> = emptyList(),
    val doubtful: Boolean = doubts.isNotEmpty(),
) {
    init {
        require(chunkId.isNotBlank()) { "ChunkPageProvenance chunkId must not be blank" }
    }
}
