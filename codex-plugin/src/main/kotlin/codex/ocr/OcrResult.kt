@file:JvmName("OcrResultAlias")
package codex.ocr

/**
 * Backward-compat typealias — delegates to the N0 contract
 * `contracts.ocr.OcrResult` (EPIC CDX-OCR-CONTRACTS US-2).
 *
 * The data class is now a pure N0 type shared by codex (N2) and
 * codebase (N1) without a N1→N2 dependency. Existing `codex.ocr.OcrResult`
 * imports resolve transparently (constructor + `of` companion factory).
 */
typealias OcrResult = contracts.ocr.OcrResult