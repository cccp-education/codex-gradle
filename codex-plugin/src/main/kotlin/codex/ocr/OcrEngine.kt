package codex.ocr

/**
 * Port for an OCR engine — transforms an [OcrRequest] into an [OcrResult].
 *
 * Boundary rule (EPIC CDX-OCR-BOUNDARY): software OCR (Tesseract) is
 * actioned by codex; AI-assisted OCR is actioned by the codebase socle.
 * This fun interface IS the injection port consumed by [OcrPipeline]:
 *
 * - [TesseractOcrEngine]: local software OCR without AI (codex-owned)
 * - AI engines live outside codex — the codebase socle implements this
 *   port (e.g. an adapter wrapping its `VisionProvider`) and the
 *   composition root injects it into [codex.tasks.CollectOcrTask].
 *   Without injection, the pipeline degrades to Tesseract-only.
 */
fun interface OcrEngine {
    fun process(request: OcrRequest): OcrResult
}
