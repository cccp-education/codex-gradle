package codex.tasks

/**
 * Detects font styles from PDF font names.
 *
 * Classifies fonts into one of five categories based on name heuristics.
 * Monospace detection covers Courier, Consolas, Monaco, Menlo, JetBrains Mono,
 * Source Code Pro, Fira Code, DejaVu Sans Mono, Liberation Mono and similar.
 */
object FontStyleDetector {

    /**
     * Detects the font style from a PDF font name string.
     *
     * @param fontName the font name as reported by PDFBox (e.g. "CourierNew-Bold")
     * @return the detected [FontStyle]
     */
    fun detect(fontName: String): FontStyle {
        val name = fontName.lowercase()

        val monospace = name.contains("courier") || name.contains("mono") ||
            name.contains("consolas") || name.contains("typewriter") ||
            name.contains("menlo") || name.contains("monaco") ||
            name.contains("source code") || name.contains("fira code") ||
            name.contains("jetbrains") || name.contains("droid sans mono") ||
            name.contains("dejavu sans mono") || name.contains("liberation mono")

        val bold = !monospace && (name.contains("bold") || name.contains("bd ") ||
            name.contains("heavy") || name.contains("black") ||
            name.matches(Regex(".*bold(mt)?$")))

        val italic = (name.contains("italic") || name.contains("oblique") ||
            name.contains("slanted") || name.contains("it ") ||
            name.matches(Regex(".*italic(mt)?$")))

        return when {
            monospace -> FontStyle.MONOSPACE
            bold && italic -> FontStyle.BOLD_ITALIC
            bold -> FontStyle.BOLD
            italic -> FontStyle.ITALIC
            else -> FontStyle.NORMAL
        }
    }
}

/**
 * Font style classification for PDF text extraction.
 *
 * @property NORMAL regular weight, upright
 * @property BOLD heavier weight variant
 * @property ITALIC slanted/oblique variant
 * @property BOLD_ITALIC combined bold and italic
 * @property MONOSPACE fixed-width font (code blocks)
 */
enum class FontStyle {
    NORMAL, BOLD, ITALIC, BOLD_ITALIC, MONOSPACE;

    /** Returns true if this style represents a monospace font. */
    fun isMonospace() = this == MONOSPACE
    /** Returns true if this style has bold weight. */
    fun isBold() = this == BOLD || this == BOLD_ITALIC
    /** Returns true if this style is italic/oblique. */
    fun isItalic() = this == ITALIC || this == BOLD_ITALIC
}
