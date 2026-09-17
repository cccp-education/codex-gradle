package codex.ocr

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Reason a scanned page is doubtful — surfaced at acquisition time.
 *
 * Boundary: this is the *acquisition* doubt signal produced by codex (where the
 * OCR happens). The document-side structural detector (`BookOcrFailureDetector`,
 * OCR-QUALITY-3) owns assembly-time structural reasons; the two must not be
 * conflated. `LOW_CONFIDENCE` is the reason only codex can produce, because
 * only codex has the real [OcrResult.confidence] (OCR-QUALITY-1).
 */
@Serializable
enum class OcrQualityReason {
    /** The vision engine could not read the page (marker emitted in the text). */
    ILLISIBLE,

    /** The page body is empty / truncated below the short threshold. */
    TOO_SHORT,

    /** The real OCR confidence (Tesseract TSV / vision) is below the low threshold. */
    LOW_CONFIDENCE,

    /** An `image::` / `image:` macro references a file not materialised next to the page. */
    IMAGE_MISSING,
}

/**
 * A single acquisition-time quality issue, localised to a page.
 *
 * @property pageId page identifier (image file name without extension)
 * @property imageFile source image file name
 * @property reason the doubt reason
 * @property detail discriminating evidence (confidence value, missing image path); `null` when self-evident
 */
@Serializable
data class OcrQualityIssue(
    val pageId: String,
    val imageFile: String,
    val reason: OcrQualityReason,
    val detail: String? = null,
)

/**
 * Aggregated acquisition-quality report over a directory of page images.
 *
 * @property imagesScanned number of images processed
 * @property issues all localised issues, in page order
 */
@Serializable
data class OcrQualityReport(
    val imagesScanned: Int,
    val issues: List<OcrQualityIssue>,
) {
    /** Counts issues per reason (reasons with zero issues are absent). */
    fun issueCountByReason(): Map<OcrQualityReason, Int> =
        issues.groupingBy { it.reason }.eachCount()
}

/**
 * Detects doubtful pages at acquisition time — the codex-side complement to
 * the document-side structural detector.
 *
 * Rules (mirroring the document-side ordering — whole-page reasons are
 * exclusive-first, structured reasons accumulate):
 * 1. `[ILLISIBLE]` marker → ILLISIBLE (never probed further);
 * 2. body below [SHORT_THRESHOLD] → TOO_SHORT (never probed further);
 * 3. real confidence below [lowConfidenceThreshold] → LOW_CONFIDENCE;
 * 4. `image::` / `image:` macro whose target is absent next to the page →
 *    IMAGE_MISSING (one issue per missing target).
 *
 * Pure, deterministic, read-only (Loi de l'Économie d'Encre — no mutation).
 * Unit-testable without Gradle or I/O beyond an optional filesystem probe.
 */
object OcrQualityAnalyzer {

    const val SHORT_THRESHOLD = 40
    const val DEFAULT_LOW_CONFIDENCE = 0.5

    private val ILLISIBLE_MARKERS = listOf("[ILLISIBLE]", "[ILLISIBL")
    // Covers the block macro `image::target[]` and the inline macro
    // `image:target[]` — the real corpus uses the inline form (ghost images
    // that a block-only regex would silently skip).
    private val IMAGE_DIRECTIVE = Regex("""image::?([^\[\s]+)\[""")

    fun analyze(
        pageId: String,
        imageFile: String,
        text: String,
        confidence: Double,
        pageDir: File,
        lowConfidenceThreshold: Double = DEFAULT_LOW_CONFIDENCE,
    ): List<OcrQualityIssue> {
        ILLISIBLE_MARKERS.firstOrNull { it in text }?.let {
            return listOf(OcrQualityIssue(pageId, imageFile, OcrQualityReason.ILLISIBLE))
        }
        if (text.trim().length < SHORT_THRESHOLD) {
            return listOf(OcrQualityIssue(pageId, imageFile, OcrQualityReason.TOO_SHORT))
        }

        val issues = mutableListOf<OcrQualityIssue>()
        if (confidence < lowConfidenceThreshold) {
            issues += OcrQualityIssue(
                pageId = pageId,
                imageFile = imageFile,
                reason = OcrQualityReason.LOW_CONFIDENCE,
                detail = formatConfidence(confidence),
            )
        }
        missingImageTargets(text)
            .filter { target -> !File(pageDir, target).isFile }
            .forEach { target ->
                issues += OcrQualityIssue(
                    pageId = pageId,
                    imageFile = imageFile,
                    reason = OcrQualityReason.IMAGE_MISSING,
                    detail = target,
                )
            }
        return issues
    }

    private fun missingImageTargets(text: String): List<String> =
        text.lines().mapNotNull { IMAGE_DIRECTIVE.find(it)?.groupValues?.getOrNull(1) }

    private fun formatConfidence(confidence: Double): String =
        "%.2f".format(java.util.Locale.ROOT, confidence)
}
