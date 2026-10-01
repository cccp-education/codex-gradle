package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.DoubtMetadata
import codebase.store.DoubtPolicy
import codebase.store.DoubtfulChunk

/**
 * Detects table-of-contents pollution in a chunk (C-4, S-233).
 *
 * The acquired corpus rendered the book TOC as headings with dot leaders
 * (`Historique du Titre ..........13`). `SemanticChunker` chunks them like any
 * other section, producing near-empty `chunks.json` entries that pollute
 * retrieval (TOC entries resurface in similarity). The root cause is upstream
 * (the scanned `livre.adoc`, ownership Tombouctou) — this object treats the
 * symptom at ingestion, exactly as the C-4 cadrage allows.
 *
 * Decision (`doubtful`, not exclusion): the TOC chunk is flagged doubtful with
 * zero confidence, riding the doubt metadata already transported N1
 * (`DoubtPolicy` / `DoubtMetadata` / `RagVectorStore.ingestWithDoubt`). No
 * re-chunking, no re-vectorisation — Loi de l'Économie d'Encre. A retrieval
 * consumer annotates or excludes it like any other doubtful passage.
 *
 * The signal is deliberately narrow: a **heading** line carrying a run of four
 * or more dots. It does not match a three-dot ellipsis (`...`), dotted
 * coordinates (`3.3.6`) or prose. A polluted section *ancestor* (a real chunk
 * whose path traverses a TOC title) is **not** flagged — the chunk itself is
 * retrievable content, only its path is noisy.
 *
 * Pure object — no I/O, no Gradle, unit-testable in isolation.
 */
object TocNoiseDetector {

    /** Doubtful TOC chunks carry zero confidence — pure typographic noise. */
    const val TOC_NOISE_CONFIDENCE = 0.0

    private val HEADING_PATTERN = Regex("""^#{1,6}\s+.+$""")
    private val DOT_LEADER_PATTERN = Regex("""\.{4,}""")

    /**
     * True when [line] is a Markdown heading whose text carries a dot leader
     * (a run of four or more dots).
     */
    fun isTocHeading(line: String): Boolean {
        val trimmed = line.trim()
        if (!HEADING_PATTERN.matches(trimmed)) return false
        return DOT_LEADER_PATTERN.containsMatchIn(trimmed)
    }

    /**
     * True when any heading line of [chunk] is a TOC entry.
     */
    fun isTocNoise(chunk: DocumentChunk): Boolean =
        chunk.content.lineSequence().any { isTocHeading(it) }

    /**
     * Derives the doubt metadata for [chunk]: a TOC-polluted chunk is doubtful
     * with zero confidence, every other chunk keeps the default full
     * confidence.
     */
    fun doubtFor(chunk: DocumentChunk): DoubtMetadata =
        if (isTocNoise(chunk)) {
            DoubtMetadata(confidence = TOC_NOISE_CONFIDENCE, doubtful = true)
        } else {
            DoubtMetadata()
        }

    /**
     * Marks every [chunk] with its doubt metadata: the N1 [DoubtPolicy]
     * (OCR `[ILLISIBLE]` markers) overlaid with the TOC-pollution detection.
     *
     * A TOC-polluted chunk is doubtful even when it carries no illisible
     * marker; elsewhere the base policy is authoritative (a no-op overlay).
     * The overlay lives in N2 because the TOC pollution is an acquisition
     * concern — the N1 socle stays agnostic and no codebase publication is
     * required.
     */
    fun markDoubt(chunks: List<DocumentChunk>): List<DoubtfulChunk> =
        chunks.map { chunk ->
            val doubt = if (isTocNoise(chunk)) doubtFor(chunk) else DoubtPolicy.derive(chunk)
            DoubtfulChunk(chunk = chunk, doubt = doubt)
        }
}
