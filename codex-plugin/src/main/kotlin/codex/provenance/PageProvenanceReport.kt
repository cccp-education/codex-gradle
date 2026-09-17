package codex.provenance

import kotlinx.serialization.Serializable

/**
 * Self-describing sidecar of the page provenance of a whole corpus
 * (`page-provenance.json`) — the artefact produced by the
 * `collectPageProvenance` task and consumed by the N3 composite context.
 *
 * The header counts make the sidecar **self-describing**: a consumer (or the
 * human iterating the scans) reads the join rate without recomputing it, and a
 * silent regression (a `SectionTitleNormalizer` change) shows up as a falling
 * `joinRate` rather than as missing pages.
 *
 * @property sourceDocument the source document stem the chunks derive from
 * @property chunkCount total number of chunks indexed
 * @property resolvedCount number of chunks whose section resolved to ≥ 1 page
 * @property joinRate `resolvedCount / chunkCount`, in [0.0, 1.0]
 * @property chunks per-chunk provenance, in chunk order
 */
@Serializable
data class PageProvenanceReport(
    val sourceDocument: String,
    val chunkCount: Int,
    val resolvedCount: Int,
    val joinRate: Double,
    val chunks: List<ChunkPageProvenance>,
) {
    init {
        require(chunkCount >= 0) { "chunkCount must be non-negative, got: $chunkCount" }
        require(resolvedCount in 0..chunkCount) {
            "resolvedCount must be in 0..chunkCount, got: $resolvedCount / $chunkCount"
        }
        require(joinRate in 0.0..1.0) { "joinRate must be in [0.0, 1.0], got: $joinRate" }
    }

    companion object {
        /** Builds the report from a resolved provenance list (derives the counts). */
        fun of(sourceDocument: String, provenance: List<ChunkPageProvenance>): PageProvenanceReport {
            val resolved = provenance.count { it.pages.isNotEmpty() }
            val rate = if (provenance.isEmpty()) 0.0 else resolved.toDouble() / provenance.size.toDouble()
            return PageProvenanceReport(
                sourceDocument = sourceDocument,
                chunkCount = provenance.size,
                resolvedCount = resolved,
                joinRate = rate,
                chunks = provenance,
            )
        }
    }
}
