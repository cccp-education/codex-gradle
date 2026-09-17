package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * OCR-QUALITY-2 — problematic-image detection at acquisition time.
 *
 * `TesseractOcrEngine` now reports a real confidence (OCR-QUALITY-1), but a
 * confidence alone is not a verdict: a page can be illegible while Tesseract
 * is confident about garbage, or reference an image that was never
 * materialised on disk. [OcrQualityAnalyzer] turns the acquisition signals
 * into explicit, localised issues so the human iteration targets the exact
 * doubtful page instead of re-reading the whole book.
 *
 * Baby-step TDD RED → GREEN → REFACTOR.
 */
class OcrQualityAnalyzerTest {

    @Test
    fun `flags an illisible marker from the vision engine`() {
        val issues = OcrQualityAnalyzer.analyze(
            pageId = "073",
            imageFile = "073.png",
            text = "Some text\n[ILLISIBLE]\nmore text",
            confidence = 0.9,
            pageDir = File("/nonexistent"),
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.ILLISIBLE, issues.single().reason)
        assertEquals("073", issues.single().pageId)
    }

    @Test
    fun `flags a near-empty page as too short`() {
        val issues = OcrQualityAnalyzer.analyze(
            pageId = "074",
            imageFile = "074.png",
            text = "few",
            confidence = 0.8,
            pageDir = File("/nonexistent"),
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.TOO_SHORT, issues.single().reason)
    }

    @Test
    fun `flags a readable page whose real confidence is below the threshold`() {
        val issues = OcrQualityAnalyzer.analyze(
            pageId = "075",
            imageFile = "075.png",
            text = "A long enough readable page body that is clearly above the short threshold.",
            confidence = 0.32,
            pageDir = File("/nonexistent"),
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.LOW_CONFIDENCE, issues.single().reason)
        assertEquals("0.32", issues.single().detail)
    }

    @Test
    fun `does not flag a confident readable page`() {
        val issues = OcrQualityAnalyzer.analyze(
            pageId = "076",
            imageFile = "076.png",
            text = "A long enough readable page body that is clearly above the short threshold.",
            confidence = 0.91,
            pageDir = File("/nonexistent"),
        )

        assertTrue(issues.isEmpty(), "a clean confident page must raise no issue")
    }

    @Test
    fun `flags a ghost image referenced but not materialised next to the page`(@TempDir dir: File) {
        val pageDir = File(dir, "pages").apply { mkdirs() }
        // The referenced image does NOT exist in pageDir → ghost.
        val text = "Body\n\nimage::cerveau_gauche_vs_cerveau_droit.jpg[]\n\nmore"

        val issues = OcrQualityAnalyzer.analyze(
            pageId = "061",
            imageFile = "061.png",
            text = text,
            confidence = 0.9,
            pageDir = pageDir,
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.IMAGE_MISSING, issues.single().reason)
        assertEquals("cerveau_gauche_vs_cerveau_droit.jpg", issues.single().detail)
    }

    @Test
    fun `does not flag a materialised image`(@TempDir dir: File) {
        val pageDir = File(dir, "pages").apply { mkdirs() }
        File(pageDir, "diagram.png").writeBytes(ByteArray(4))
        val text = "A long enough readable page body above the short threshold.\n\nimage::diagram.png[]"

        val issues = OcrQualityAnalyzer.analyze(
            pageId = "062",
            imageFile = "062.png",
            text = text,
            confidence = 0.9,
            pageDir = pageDir,
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `detects the inline image macro form used by the real corpus`(@TempDir dir: File) {
        val pageDir = File(dir, "pages").apply { mkdirs() }
        val text = "A long enough readable page body above the short threshold.\n" +
            "Body image:arrow-curved-left[] tail."

        val issues = OcrQualityAnalyzer.analyze(
            pageId = "083_1",
            imageFile = "083_1.png",
            text = text,
            confidence = 0.9,
            pageDir = pageDir,
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.IMAGE_MISSING, issues.single().reason)
        assertEquals("arrow-curved-left", issues.single().detail)
    }

    @Test
    fun `a page can carry several structured issues at once`(@TempDir dir: File) {
        val pageDir = File(dir, "pages").apply { mkdirs() }
        val text = "A long enough readable page body above the short threshold.\n" +
            "image::missing.png[]"

        val issues = OcrQualityAnalyzer.analyze(
            pageId = "084",
            imageFile = "084.png",
            text = text,
            confidence = 0.4,
            pageDir = pageDir,
        )

        val reasons = issues.map { it.reason }.toSet()
        assertTrue(OcrQualityReason.LOW_CONFIDENCE in reasons)
        assertTrue(OcrQualityReason.IMAGE_MISSING in reasons)
    }

    @Test
    fun `whole-page reasons are exclusive-first - an illisible page is not probed further`(@TempDir dir: File) {
        val pageDir = File(dir, "pages").apply { mkdirs() }
        val text = "[ILLISIBLE] image::missing.png[]"

        val issues = OcrQualityAnalyzer.analyze(
            pageId = "085",
            imageFile = "085.png",
            text = text,
            confidence = 0.0,
            pageDir = pageDir,
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.ILLISIBLE, issues.single().reason)
    }

    @Test
    fun `low confidence threshold is configurable`() {
        val text = "A long enough readable page body that is clearly above the short threshold."
        val issues = OcrQualityAnalyzer.analyze(
            pageId = "086",
            imageFile = "086.png",
            text = text,
            confidence = 0.55,
            pageDir = File("/nonexistent"),
            lowConfidenceThreshold = 0.6,
        )

        assertEquals(1, issues.size)
        assertEquals(OcrQualityReason.LOW_CONFIDENCE, issues.single().reason)
    }

    @Test
    fun `report aggregates issues and counts the images scanned`() {
        val issues = listOf(
            OcrQualityIssue("073", "073.png", OcrQualityReason.ILLISIBLE, null),
            OcrQualityIssue("075", "075.png", OcrQualityReason.LOW_CONFIDENCE, "0.32"),
            OcrQualityIssue("076", "076.png", OcrQualityReason.LOW_CONFIDENCE, "0.40"),
        )

        val report = OcrQualityReport(imagesScanned = 10, issues = issues)

        assertEquals(10, report.imagesScanned)
        assertEquals(3, report.issues.size)
        assertEquals(1, report.issueCountByReason()[OcrQualityReason.ILLISIBLE])
        assertEquals(2, report.issueCountByReason()[OcrQualityReason.LOW_CONFIDENCE])
    }
}
