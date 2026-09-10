package codex.enrichment

/**
 * Pure object that normalizes section titles into one canonical form so
 * that LDD nodes (AsciidoctorJ AST), RAG chunks (SemanticChunker) and
 * Graphify graph labels can be joined on the same section despite
 * divergent encodings.
 *
 * The S-217 enrichment run proved that the same section is encoded
 * differently by each parser:
 *  - LDD: HTML entities (`&#8217;`, `&#8230;&#8203;`), footnotes as
 *    `<sup class="footnote">[...]</sup>` markup.
 *  - Chunks: typographic apostrophes (`'`), AsciiDoc footnote macros
 *    (`footnote:[102]`), ASCII dot leaders plus page number (`......24`).
 *
 * The pipeline applied in order:
 * 1. HTML unescape (`&#8217;` → `'`, `&#8230;` → `…`, `&amp;` → `&`).
 * 2. Strip AsciiDoc footnote macros (`footnote:[102]`).
 * 3. Strip HTML tags (`<sup ...>...</sup>`).
 * 4. Remove zero-width space characters (`\u200b`).
 * 5. Strip trailing dot leaders (ASCII dots, unicode ellipsis, mixed runs
 *    with internal spaces) and optional trailing page number.
 * 6. Collapse whitespace runs.
 * 7. Lowercase.
 *
 * The normalizer is deterministic, side-effect free and idempotent.
 */
object SectionTitleNormalizer {

    private val htmlEntity = Regex("""&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+[0-9]*);""")
    private val footnoteMacro = Regex("""footnote:\[\d+]""")
    private val htmlTag = Regex("""<[^>]*>""")
    private val footnoteMarker = Regex("""\[\d+]\s*$""")
    private val trailingDotLeaders = Regex("""[.\u2026\u00b7][.\u2026\u00b7\s]*\d*\s*$""")
    private val whitespaceRun = Regex("""\s+""")

    /**
     * Returns the canonical form of [title], or the empty string for
     * blank or dot-leader-only titles.
     */
    fun normalize(raw: String): String {
        var s = unescape(raw)
        s = s.replace(footnoteMacro, "")
        s = s.replace(htmlTag, "")
        s = s.replace("\u200b", "")
        s = s.replace(footnoteMarker, "")
        s = s.replace(trailingDotLeaders, "")
        s = s.replace(whitespaceRun, " ")
        return s.trim().lowercase()
            .replace('\u2019', '\'')
            .replace('\u2018', '\'')
            .replace('\u02bc', '\'')
    }

    /**
     * Unescapes decimal and hexadecimal numeric HTML entities plus the
     * handful of named entities that AsciidoctorJ emits in headings
     * (amp, lt, gt, quot, apos). Unknown entities are left verbatim.
     */
    private fun unescape(raw: String): String {
        if (!raw.contains('&')) return raw
        return htmlEntity.replace(raw) { match ->
            val entity = match.groupValues[1]
            val decoded: String? = when {
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.substring(2).toIntOrNull(16)?.let { it.toCodePointOrNull() }
                entity.startsWith("#") ->
                    entity.substring(1).toIntOrNull()?.let { it.toCodePointOrNull() }
                else -> namedEntities[entity]
            }
            decoded ?: match.value
        }
    }

    private fun Int.toCodePointOrNull(): String? =
        if (this in 0..0x10FFFF) String(Character.toChars(this)) else null

    private val namedEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )
}