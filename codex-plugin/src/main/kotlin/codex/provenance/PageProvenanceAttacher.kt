package codex.provenance

import codebase.store.DocumentChunk

/**
 * Attaches the derived page provenance to the ingestion chunks (N2, codex).
 *
 * EPIC CB-PAGE-PROVENANCE US-2 — transport N2. The sidecar
 * `page-provenance.json` ([PageProvenanceReport], S-224) keys its chunks on
 * the exact SHA-256 [ChunkPageProvenance.chunkId]; the ingestion payload
 * keys its chunks on the exact [DocumentChunk.id] — the **same** value. The
 * join at ingestion time is therefore **exact**, unlike the retrieval join by
 * `sectionPath` (heuristic, 70.8% measured — dev. D6 S-224).
 *
 * The page is a metadata **transported** (like the doubt, DOUBT-BRIDGE S-219):
 * N2 derives it, N1 carries it (`DocumentChunk.pages`), the retrieval exposes
 * it. This object is the N2 bridge that fills the carrier.
 *
 * Deterministic, side-effect free, degrades silently: a null report, an
 * unmatched id or an empty page list leaves the chunk untouched (Économie
 * d'Encre — a sidecar that adds nothing is not even allocated around).
 */
object PageProvenanceAttacher {

    /**
     * Attaches to every chunk the page(s) the sidecar resolves for its
     * `id`, preserving the pages already carried.
     *
     * @param chunks the ingestion chunks (as decoded from `chunks.json`)
     * @param report the optional page provenance sidecar; `null` degrades to
     *   the original list
     * @return the chunks with their resolved pages; the **same** list instance
     *   when the sidecar adds nothing (no needless allocation)
     */
    fun attach(chunks: List<DocumentChunk>, report: PageProvenanceReport?): List<DocumentChunk> {
        if (chunks.isEmpty()) return chunks
        val pagesById = report?.chunks
            ?.filter { it.pages.isNotEmpty() }
            ?.associate { it.chunkId to it.pages }
            ?: return chunks
        if (pagesById.isEmpty()) return chunks
        var changed = false
        val attached = chunks.map { chunk ->
            val pages = pagesById[chunk.id]
            if (pages == null || pages == chunk.pages) {
                chunk
            } else {
                changed = true
                chunk.copy(pages = pages)
            }
        }
        return if (changed) attached else chunks
    }
}
