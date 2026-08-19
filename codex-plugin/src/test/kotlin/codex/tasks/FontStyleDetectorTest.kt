package codex.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Isolated unit tests for [FontStyleDetector] — characterization of the
 * existing object (CDX-7-1, baby-step TDD GREEN: code already exists).
 *
 * Covers every branch of [FontStyleDetector.detect]: monospace keywords
 * (12 sub-conditions), bold keywords, italic keywords, the monospace
 * priority guard, the bold+italic combination, and the normal fallback.
 * Also covers the [FontStyle] enum helpers [isMonospace]/[isBold]/[isItalic]
 * for all five values.
 */
class FontStyleDetectorTest {

    // ── Monospace detection — each keyword triggers MONOSPACE ──────────────────

    @Test
    fun `courier font detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Courier"))
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("CourierNew"))
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("CourierNew-Bold"))
    }

    @Test
    fun `mono keyword detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("DejaVuMono"))
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("UbuntuMono"))
    }

    @Test
    fun `consolas detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Consolas"))
    }

    @Test
    fun `typewriter detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Typewriter"))
    }

    @Test
    fun `menlo detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Menlo"))
    }

    @Test
    fun `monaco detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Monaco"))
    }

    @Test
    fun `source code pro detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Source Code Pro"))
    }

    @Test
    fun `fira code detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Fira Code"))
    }

    @Test
    fun `jetbrains detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("JetBrains Mono"))
    }

    @Test
    fun `droid sans mono detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Droid Sans Mono"))
    }

    @Test
    fun `dejavu sans mono detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("DejaVu Sans Mono"))
    }

    @Test
    fun `liberation mono detected as monospace`() {
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("Liberation Mono"))
    }

    @Test
    fun `monospace takes priority over bold keyword`() {
        // CourierNew-Bold contains "bold" but also "courier" → MONOSPACE wins
        val style = FontStyleDetector.detect("CourierNew-Bold")
        assertEquals(FontStyle.MONOSPACE, style)
        assertTrue(style.isMonospace())
        assertFalse(style.isBold())
    }

    // ── Bold detection (non-monospace) ─────────────────────────────────────────

    @Test
    fun `bold keyword detected as bold`() {
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("Helvetica-Bold"))
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("Arial Bold"))
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("DejaVu Sans Bold"))
    }

    @Test
    fun `heavy keyword detected as bold`() {
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("Helvetica-Heavy"))
    }

    @Test
    fun `black keyword detected as bold`() {
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("Inter-Black"))
    }

    @Test
    fun `bd suffix keyword detected as bold`() {
        // "bd " with trailing space — matched only when followed by space
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("DIN bd Regular"))
        // Hyphen-bound "bd" is NOT matched (no trailing space) — documents current behavior
        assertEquals(FontStyle.NORMAL, FontStyleDetector.detect("DIN-bd-Regular"))
    }

    @Test
    fun `boldmt regex detected as bold`() {
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("Helvetica-BoldMT"))
    }

    // ── Italic detection ───────────────────────────────────────────────────────

    @Test
    fun `italic keyword detected as italic`() {
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Times-Italic"))
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Georgia Italic"))
    }

    @Test
    fun `oblique keyword detected as italic`() {
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Helvetica-Oblique"))
    }

    @Test
    fun `slanted keyword detected as italic`() {
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Slanted"))
    }

    @Test
    fun `it suffix keyword detected as italic`() {
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Helvetica it Regular"))
    }

    @Test
    fun `italicmt regex detected as italic`() {
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("Times-ItalicMT"))
    }

    // ── Bold + italic combination ──────────────────────────────────────────────

    @Test
    fun `bold italic combination detected as bold_italic`() {
        assertEquals(FontStyle.BOLD_ITALIC, FontStyleDetector.detect("Helvetica-BoldItalic"))
        assertEquals(FontStyle.BOLD_ITALIC, FontStyleDetector.detect("Arial Bold Italic"))
        assertEquals(FontStyle.BOLD_ITALIC, FontStyleDetector.detect("Times-BoldOblique"))
    }

    @Test
    fun `bold italic is both bold and italic`() {
        val style = FontStyleDetector.detect("Helvetica-BoldItalic")
        assertTrue(style.isBold())
        assertTrue(style.isItalic())
        assertFalse(style.isMonospace())
    }

    // ── Normal fallback ────────────────────────────────────────────────────────

    @Test
    fun `regular sans-serif font detected as normal`() {
        assertEquals(FontStyle.NORMAL, FontStyleDetector.detect("Helvetica"))
        assertEquals(FontStyle.NORMAL, FontStyleDetector.detect("Arial"))
        assertEquals(FontStyle.NORMAL, FontStyleDetector.detect("Times-Roman"))
    }

    @Test
    fun `normal style has no flags`() {
        val style = FontStyleDetector.detect("Helvetica")
        assertFalse(style.isBold())
        assertFalse(style.isItalic())
        assertFalse(style.isMonospace())
    }

    @Test
    fun `empty font name detected as normal`() {
        assertEquals(FontStyle.NORMAL, FontStyleDetector.detect(""))
    }

    @Test
    fun `case insensitive detection`() {
        assertEquals(FontStyle.BOLD, FontStyleDetector.detect("HELVETICA-BOLD"))
        assertEquals(FontStyle.ITALIC, FontStyleDetector.detect("helvetica-italic"))
        assertEquals(FontStyle.MONOSPACE, FontStyleDetector.detect("COURIER"))
    }

    // ── FontStyle enum helpers — all five values ───────────────────────────────

    @Test
    fun `FontStyle isMonospace only for MONOSPACE`() {
        assertTrue(FontStyle.MONOSPACE.isMonospace())
        assertFalse(FontStyle.NORMAL.isMonospace())
        assertFalse(FontStyle.BOLD.isMonospace())
        assertFalse(FontStyle.ITALIC.isMonospace())
        assertFalse(FontStyle.BOLD_ITALIC.isMonospace())
    }

    @Test
    fun `FontStyle isBold for BOLD and BOLD_ITALIC`() {
        assertTrue(FontStyle.BOLD.isBold())
        assertTrue(FontStyle.BOLD_ITALIC.isBold())
        assertFalse(FontStyle.NORMAL.isBold())
        assertFalse(FontStyle.ITALIC.isBold())
        assertFalse(FontStyle.MONOSPACE.isBold())
    }

    @Test
    fun `FontStyle isItalic for ITALIC and BOLD_ITALIC`() {
        assertTrue(FontStyle.ITALIC.isItalic())
        assertTrue(FontStyle.BOLD_ITALIC.isItalic())
        assertFalse(FontStyle.NORMAL.isItalic())
        assertFalse(FontStyle.BOLD.isItalic())
        assertFalse(FontStyle.MONOSPACE.isItalic())
    }
}