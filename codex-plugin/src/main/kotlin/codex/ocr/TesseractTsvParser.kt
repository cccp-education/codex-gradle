package codex.ocr

/**
 * Parsed Tesseract TSV output — mean word-level confidence.
 *
 * Pure value object (no Gradle, no I/O) returned by [TesseractTsvParser.parse].
 *
 * @property meanConfidence mean word confidence in [0.0, 1.0]
 * @property wordCount number of word-level rows counted (confidence >= 0, non-blank text)
 */
data class ParsedTesseractConfidence(
    val meanConfidence: Double,
    val wordCount: Int
)

/**
 * Parses Tesseract's TSV output into a real confidence measure.
 *
 * Tesseract's plain-text output carries no confidence; its TSV config emits a
 * per-word `conf` column (0..100, `-1` for non-word container rows). This
 * parser turns those rows into the mean confidence in [0,1] consumed by the
 * N0 `contracts.ocr.OcrResult` contract — replacing the historical hardcoded
 * `0.7` placeholder (OCR-QUALITY-1).
 *
 * Rules (Loi de l'Économie d'Encre — the signal must be honest):
 * - only word-level rows (`level == 5`) are counted;
 * - rows with a negative confidence (container/unreliable) are skipped;
 * - rows with blank text are skipped (a garbage image emits whitespace words
 *   with high confidence — counting them would hide the very doubt OCR-QUALITY
 *   must surface);
 * - malformed rows are skipped silently (best-effort, never throws);
 * - an empty/no-word TSV yields `meanConfidence = 0.0`.
 *
 * Pure, deterministic, unit-testable.
 */
object TesseractTsvParser {

    private const val WORD_LEVEL = "5"
    private const val CONFIDENCE_COLUMN = 10
    private const val TEXT_COLUMN = 11

    fun parse(tsv: String): ParsedTesseractConfidence {
        var sum = 0.0
        var count = 0

        tsv.lineSequence()
            .drop(1) // header row
            .forEach { line ->
                if (line.isBlank()) return@forEach
                val columns = line.split('\t')
                if (columns.size <= TEXT_COLUMN) return@forEach
                if (columns[0] != WORD_LEVEL) return@forEach
                if (columns[TEXT_COLUMN].isBlank()) return@forEach

                val confidence = columns[CONFIDENCE_COLUMN].toDoubleOrNull() ?: return@forEach
                if (confidence < 0.0) return@forEach

                sum += confidence
                count++
            }

        val mean = if (count == 0) 0.0 else sum / count / 100.0
        return ParsedTesseractConfidence(
            meanConfidence = mean.coerceIn(0.0, 1.0),
            wordCount = count
        )
    }
}
