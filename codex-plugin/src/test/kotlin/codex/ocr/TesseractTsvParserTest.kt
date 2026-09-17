package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * OCR-QUALITY-1 — Tesseract true confidence.
 *
 * `TesseractOcrEngine` used to hardcode `confidence = 0.7` for any non-empty
 * text — a placeholder that made every page indistinguishable from a clean
 * one. Tesseract's TSV output carries a per-word confidence (0..100); this
 * pure parser turns it into the mean confidence in [0,1] consumed by the N0
 * `OcrResult` contract.
 *
 * Baby-step TDD RED → GREEN → REFACTOR.
 */
class TesseractTsvParserTest {

    @Test
    fun `parses mean confidence of word level rows`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            1	1	0	0	0	0	0	0	400	80	-1	
            2	1	1	0	0	0	12	26	330	29	-1	
            5	1	1	1	1	1	12	26	112	29	96.323288	HELLO
            5	1	1	1	1	2	139	26	126	29	94.551613	WORLD
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertEquals(2, parsed.wordCount)
        assertEquals((96.323288 + 94.551613) / 2 / 100.0, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `returns zero confidence when no word level rows`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            1	1	0	0	0	0	0	0	400	80	-1	
            2	1	1	0	0	0	12	26	330	29	-1	
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertEquals(0, parsed.wordCount)
        assertEquals(0.0, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `returns zero confidence for blank tsv`() {
        val parsed = TesseractTsvParser.parse("")

        assertEquals(0, parsed.wordCount)
        assertEquals(0.0, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `ignores word rows whose text is blank`() {
        // Tesseract can emit a word row with a high confidence but whitespace
        // text on garbage images — counting it would inflate the confidence of
        // a page that read nothing (the very signal OCR-QUALITY must catch).
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	0	0	10	10	95.000000	 
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertEquals(0, parsed.wordCount)
        assertEquals(0.0, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `ignores word rows with negative confidence`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	12	26	112	29	-1	NOISE
            5	1	1	1	1	2	139	26	126	29	80.0	REAL
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertEquals(1, parsed.wordCount)
        assertEquals(0.8, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `skips malformed rows without failing`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	12	26	112	29	not-a-number	BROKEN
            5	1	1	1	1	2	139	26	126	29	90.0	OK
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertEquals(1, parsed.wordCount)
        assertEquals(0.9, parsed.meanConfidence, 0.0001)
    }

    @Test
    fun `mean confidence stays within zero and one`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	12	26	112	29	100.0	PERFECT
        """.trimIndent()

        val parsed = TesseractTsvParser.parse(tsv)

        assertTrue(parsed.meanConfidence in 0.0..1.0)
        assertEquals(1.0, parsed.meanConfidence, 0.0001)
    }
}
