package codex.bdd

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.nio.file.Files

/**
 * Dedicated steps for OCR-QUALITY-1 BDD scenarios (`@ocr-quality`).
 *
 * Phrases are intentionally distinct from [OcrPipelineSteps] and
 * [OcrBoundarySteps] to avoid DuplicateStepDefinition collisions — all three
 * share the `codex.bdd` glue package.
 *
 * The scenarios lock three contracts:
 *
 * 1. Tesseract TSV word scores are averaged into a normalized confidence
 *    (replacing the historical hardcoded 0.7 placeholder).
 * 2. A garbage image whose only "words" are whitespace yields zero
 *    confidence and zero word count (the doubt must not be masked).
 * 3. A degraded engine (no TSV) preserves its text and flags the doubt —
 *    and the pipeline does not discard that text at confidence 0.0
 *    (confidence is a doubt signal, not a fallback gate).
 */
class OcrQualitySteps {

    private val state = mutableMapOf<String, Any>()

    // ── TSV confidence parsing ──────────────────────────────────────────

    @Given("a Tesseract TSV output with word confidences {string} and {string}")
    fun aTesseractTsvWithWordConfidences(first: String, second: String) {
        state["tsv"] = tsv(
            "5\t1\t1\t1\t1\t1\t12\t26\t112\t29\t$first\tHELLO",
            "5\t1\t1\t1\t1\t2\t139\t26\t126\t29\t$second\tWORLD",
        )
    }

    @Given("a Tesseract TSV output with only a blank word at confidence {string}")
    fun aTesseractTsvWithOnlyBlankWord(confidence: String) {
        state["tsv"] = tsv("5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t$confidence\t ")
    }

    @When("the Tesseract TSV is parsed for confidence")
    fun theTesseractTsvIsParsed() {
        val tsv = state["tsv"] as String
        state["parsed"] = codex.ocr.TesseractTsvParser.parse(tsv)
    }

    @Then("the parsed confidence is {double}")
    fun theParsedConfidenceIs(expected: Double) {
        val parsed = state["parsed"] as codex.ocr.ParsedTesseractConfidence
        assertEquals(expected, parsed.meanConfidence, 0.0001)
    }

    @And("the parsed word count is {int}")
    fun theParsedWordCountIs(expected: Int) {
        val parsed = state["parsed"] as codex.ocr.ParsedTesseractConfidence
        assertEquals(expected, parsed.wordCount)
    }

    // ── Degraded engine (no TSV) ────────────────────────────────────────

    @Given("a stub tesseract that writes {string} without a TSV file")
    fun aStubTesseractWithoutTsv(text: String) {
        val dir = Files.createTempDirectory("ocr-quality-stub").toFile()
        val stub = File(dir, "stub-tesseract.sh")
        stub.writeText(
            """
            #!/bin/sh
            # ${'$'}1=input ${'$'}2=outputBase
            echo "$text" > "${'$'}2.txt"
            """.trimIndent()
        )
        stub.setExecutable(true)
        state["stub"] = stub
    }

    @When("the TesseractOcrEngine processes an image with that stub")
    fun theTesseractOcrEngineProcessesWithStub() {
        val stub = state["stub"] as File
        val dir = stub.parentFile
        val image = File(dir, "img.png")
        image.writeBytes(minimalPng())

        val engine = codex.ocr.TesseractOcrEngine(tesseractPath = stub.absolutePath)
        state["engineResult"] = engine.process(
            codex.ocr.OcrRequest(image.readBytes(), "image/png", "eng")
        )
    }

    @Then("the engine text is {string}")
    fun theEngineTextIs(expected: String) {
        val result = state["engineResult"] as codex.ocr.OcrResult
        assertEquals(expected, result.structuredText.trim())
    }

    @And("the engine confidence source is {string}")
    fun theEngineConfidenceSourceIs(expected: String) {
        val result = state["engineResult"] as codex.ocr.OcrResult
        assertEquals(expected, result.metadata["confidenceSource"])
    }

    @And("the engine confidence is {double}")
    fun theEngineConfidenceIs(expected: Double) {
        val result = state["engineResult"] as codex.ocr.OcrResult
        assertEquals(expected, result.confidence, 0.0001)
    }

    // ── Pipeline preserves degraded text ────────────────────────────────

    @Given("a degraded OCR engine that returns {string} at confidence {double}")
    fun aDegradedOcrEngineThatReturns(text: String, confidence: Double) {
        state["degradedEngine"] = object : codex.ocr.OcrEngine {
            override fun process(request: codex.ocr.OcrRequest): codex.ocr.OcrResult =
                codex.ocr.OcrResult.of(
                    text = text,
                    confidence = confidence,
                    language = request.language,
                    model = "tesseract",
                    metadata = mapOf("engine" to "tesseract", "confidenceSource" to "degraded")
                )
        }
    }

    @When("the pipeline processes an image with that degraded engine")
    fun thePipelineProcessesWithDegradedEngine() {
        val engine = state["degradedEngine"] as codex.ocr.OcrEngine
        val pipeline = codex.ocr.OcrPipeline(listOf(engine))
        state["pipelineResult"] = pipeline.process(
            codex.ocr.OcrRequest(ByteArray(8), "image/png", "fr")
        )
    }

    @Then("the pipeline text is {string}")
    fun thePipelineTextIs(expected: String) {
        val result = state["pipelineResult"] as codex.ocr.OcrResult
        assertEquals(expected, result.structuredText)
    }

    @And("the pipeline confidence is {double}")
    fun thePipelineConfidenceIs(expected: Double) {
        val result = state["pipelineResult"] as codex.ocr.OcrResult
        assertEquals(expected, result.confidence, 0.0001)
    }

    // ── Acquisition quality (OCR-QUALITY-2) ─────────────────────────────

    @Given("an acquisition page {string} with text {string} and confidence {double}")
    fun anAcquisitionPage(pageId: String, text: String, confidence: Double) {
        val dir = Files.createTempDirectory("ocr-quality-acq").toFile()
        state["acqPageId"] = pageId
        state["acqText"] = text.replace("\\n", "\n")
        state["acqConfidence"] = confidence
        state["acqDir"] = dir
    }

    @When("the acquisition quality is analysed")
    fun theAcquisitionQualityIsAnalysed() {
        state["acqIssues"] = codex.ocr.OcrQualityAnalyzer.analyze(
            pageId = state["acqPageId"] as String,
            imageFile = "${state["acqPageId"]}.png",
            text = state["acqText"] as String,
            confidence = state["acqConfidence"] as Double,
            pageDir = state["acqDir"] as File,
        )
    }

    @Then("the acquisition issue reasons are {string}")
    fun theAcquisitionIssueReasonsAre(expected: String) {
        @Suppress("UNCHECKED_CAST")
        val issues = state["acqIssues"] as List<codex.ocr.OcrQualityIssue>
        val actual = issues.joinToString(",") { it.reason.name }
        assertEquals(expected, actual)
    }

    @And("the acquisition issue count is {int}")
    fun theAcquisitionIssueCountIs(expected: Int) {
        @Suppress("UNCHECKED_CAST")
        val issues = state["acqIssues"] as List<codex.ocr.OcrQualityIssue>
        assertEquals(expected, issues.size)
    }

    @And("the acquisition issue detail is {string}")
    fun theAcquisitionIssueDetailIs(expected: String) {
        @Suppress("UNCHECKED_CAST")
        val issues = state["acqIssues"] as List<codex.ocr.OcrQualityIssue>
        val details = issues.mapNotNull { it.detail }
        assertEquals(true, details.contains(expected), "expected a detail '$expected' in $details")
    }

    // ── Helpers ─────────────────────────────────────────────────────────
    private fun tsv(vararg rows: String): String =
        (listOf("level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext") + rows)
            .joinToString("\n")

    private fun minimalPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(),
    )
}
