package codex.provenance

import codex.enrichment.SectionTitleNormalizer

/**
 * Pure normalizer for the TOC ↔ chunk title join.
 *
 * Composes the proven [SectionTitleNormalizer] canon (S-217, 115/115 on the
 * LDD ↔ chunks ↔ graphify join) with two OCR-acquisition refinements that the
 * chunker leaves in the section titles:
 *
 * 1. **Leading numeric reference** — the scanned pages carry their own
 *    numbering (`1-1.1 Identifier les différents référentiels…`,
 *    `2.1.2 Identifier et choisir…`) while the TOC title omits it. A
 *    multi-part reference (`<digit>(-|.|–)<digit>…`) is stripped even when
 *    glued to the title (`1-2.1créer…`); a single number is stripped only when
 *    followed by whitespace, so an in-title abbreviation (`les 3C`) is
 *    preserved.
 * 2. **Trailing punctuation** — OCR headings end with a colon or dot leaders
 *    (`Les compétences.`, `Comprendre la démarche 57`); the TOC title does not.
 *    Trailing `:`, `.`, ellipsis, middle-dot and whitespace runs are stripped.
 *
 * The join rate on the real FPA corpus is measured by
 * `FpaPageProvenanceJoinRateTest` (S-223 cadrage risk). Deterministic,
 * side-effect free, idempotent — never fails.
 */
object ProvenanceTitleNormalizer {

    private val leadingRef = Regex("""^(?:\d+(?:[-\u2013.]\d+)+\.?\s*|\d+\.?\s+)""")
    private val trailingPunctuation = Regex("""[\s:.\u2026\u00b7]+$""")

    /**
     * Returns the canonical join form of [raw], or the empty string when it
     * carries no usable title.
     */
    fun normalize(raw: String): String {
        val base = SectionTitleNormalizer.normalize(raw)
        if (base.isEmpty()) return ""
        return trailingPunctuation.replace(leadingRef.replace(base, ""), "").trim()
    }
}
