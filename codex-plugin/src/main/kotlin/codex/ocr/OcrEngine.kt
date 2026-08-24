@file:JvmName("OcrEngineAlias")
package codex.ocr

/**
 * Backward-compat typealias — delegates to the N0 contract
 * `contracts.ocr.OcrEngine` (EPIC CDX-OCR-CONTRACTS US-2).
 *
 * The port is now a pure N0 type shared by codex (N2) and codebase (N1)
 * without a N1→N2 dependency. Existing `codex.ocr.OcrEngine` imports
 * resolve transparently.
 */
typealias OcrEngine = contracts.ocr.OcrEngine