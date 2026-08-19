package codex.licence

import codex.LicenseZone
import java.io.File
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Detects the license zone of a PDF document by scanning its extracted text.
 *
 * This detector is an *additive* complement to the existing [codex.LicenseZoneDetector]
 * (which detects by project path). When the PDF content yields
 * [LicenseZone.UNKNOWN], the caller falls back to path-based detection.
 *
 * Precedence rules (first match wins, in declaration order):
 *
 *  1. Apache License 2.0 / Apache-2.0              -> [LicenseZone.OSS]
 *  2. Creative Commons / CC-BY / CC0               -> [LicenseZone.OSS]
 *  3. © / Copyright / All rights reserved           -> [LicenseZone.CSS]
 *  4. No license mention                            -> [LicenseZone.UNKNOWN]
 *
 * The detection is case-insensitive and does not modify its inputs.
 */
object PdfLicenseDetector {

    private val ossPatterns: List<Regex> = listOf(
        Regex("""(?i)apache\s+license\s+2\.0"""),
        Regex("""(?i)apache-2\.0"""),
        Regex("""(?i)creative\s+commons"""),
        Regex("""(?i)cc-by"""),
        Regex("""(?i)\bcc0\b""")
    )

    private val cssPatterns: List<Regex> = listOf(
        Regex("""(?i)©"""),
        Regex("""(?i)copyright"""),
        Regex("""(?i)all\s+rights\s+reserved""")
    )

    /**
     * Detects the license zone from the text already extracted from a PDF.
     *
     * Pure, deterministic, side-effect free. Use this overload when the
     * caller has already extracted the PDF text (e.g. in a pipeline step).
     *
     * @param text the extracted text of the PDF (may be blank).
     * @return the detected [LicenseZone], or [LicenseZone.UNKNOWN] when no
     *         license marker is found.
     */
    fun detect(text: String): LicenseZone {
        if (text.isBlank()) return LicenseZone.UNKNOWN
        if (ossPatterns.any { it.containsMatchIn(text) }) return LicenseZone.OSS
        if (cssPatterns.any { it.containsMatchIn(text) }) return LicenseZone.CSS
        return LicenseZone.UNKNOWN
    }

    /**
     * Detects the license zone by reading the given PDF file.
     *
     * The file is loaded with PDFBox and its text extracted with
     * [PDFTextStripper], then delegated to [detect]. When the file does
     * not exist, is not a valid PDF, or contains no extractable text,
     * [LicenseZone.UNKNOWN] is returned (degraded — the caller falls back
     * to path-based detection).
     *
     * @param pdfFile the PDF file to scan.
     * @return the detected [LicenseZone], or [LicenseZone.UNKNOWN] on any
     *         error or absence of license marker.
     */
    fun detect(pdfFile: File): LicenseZone {
        if (!pdfFile.exists() || !pdfFile.isFile) return LicenseZone.UNKNOWN
        return runCatching {
            Loader.loadPDF(pdfFile).use { doc ->
                val text = PDFTextStripper().getText(doc)
                detect(text)
            }
        }.getOrDefault(LicenseZone.UNKNOWN)
    }
}