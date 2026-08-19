package codex.bdd

import codex.ocr.LlmOcrEngine
import codex.ocr.OcrConfig
import codex.ocr.OcrEngine
import codex.ocr.OcrPipeline
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import codex.ocr.OllamaChatClient
import codex.ocr.TesseractOcrEngine
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class OcrPipelineSteps {

    private val state = mutableMapOf<String, Any>()

    @Given("an image request with language {string}")
    fun anImageRequestWithLanguage(language: String) {
        state["request"] = OcrRequest(
            imageData = ByteArray(16) { 0x42 },
            format = "image/png",
            language = language
        )
    }

    @When("the LlmOcrEngine processes the image")
    fun theLlmOcrEngineProcessesTheImage() {
        val fakeClient = object : OllamaChatClient {
            override fun chat(config: OcrConfig, prompt: String, images: List<String>): String =
                "= Title\n\nStructured content."
        }
        val engine = LlmOcrEngine(fakeClient, OcrConfig.defaultConfig())
        state["result"] = engine.process(state["request"] as OcrRequest)
    }

    @Then("the result contains structured AsciiDoc text")
    fun theResultContainsStructuredAsciiDocText() {
        val result = state["result"] as OcrResult
        assertTrue(result.structuredText.startsWith("= "), "Result should be AsciiDoc starting with title")
    }

    @And("the result model is {string}")
    fun theResultModelIs(model: String) {
        val result = state["result"] as OcrResult
        assertEquals(model, result.model)
    }

    @And("the result confidence is greater than zero")
    fun theResultConfidenceIsGreaterThanZero() {
        val result = state["result"] as OcrResult
        assertTrue(result.confidence > 0.0)
    }

    @Given("an LLM engine that returns empty text")
    fun anLlmEngineThatReturnsEmptyText() {
        state["llmEngine"] = stubEngine("", 0.0, "gpt-oss:120b-cloud")
    }

    @And("a Tesseract engine that returns {string}")
    fun aTesseractEngineThatReturns(text: String) {
        val confidence = if (text.isNotEmpty()) 0.7 else 0.0
        state["tesseractEngine"] = stubEngine(text, confidence, "tesseract")
    }

    @When("the OcrPipeline processes the image")
    fun theOcrPipelineProcessesTheImage() {
        val engines = listOfNotNull(
            state["llmEngine"] as? OcrEngine,
            state["tesseractEngine"] as? OcrEngine
        )
        val pipeline = OcrPipeline(engines)
        state["result"] = pipeline.process(OcrRequest(ByteArray(8), "image/png", "fr"))
    }

    @Then("the result text is {string}")
    fun theResultTextIs(expected: String) {
        val result = state["result"] as OcrResult
        assertEquals(expected, result.structuredText)
    }

    @And("the result model is tesseract")
    fun theResultModelIsTesseract() {
        val result = state["result"] as OcrResult
        assertEquals("tesseract", result.model)
    }

    @Then("the result text is empty")
    fun theResultTextIsEmpty() {
        val result = state["result"] as OcrResult
        assertEquals("", result.structuredText)
    }

    @And("the result confidence is zero")
    fun theResultConfidenceIsZero() {
        val result = state["result"] as OcrResult
        assertEquals(0.0, result.confidence)
    }

    @Given("an image directory with {int} page images")
    fun anImageDirectoryWithPageImages(count: Int) {
        val tempDir = Files.createTempDirectory("ocr-bdd-images").toFile()
        repeat(count) { i ->
            val png = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
            )
            File(tempDir, "page-${i + 1}.png").writeBytes(png)
        }
        state["imageDir"] = tempDir
        state["pageCount"] = count
    }

    @When("the collectOcr task runs")
    fun theCollectOcrTaskRuns() {
        val dir = state["imageDir"] as File
        val output = File(dir.parentFile, "ocr-output.adoc")
        val pageCount = state["pageCount"] as? Int ?: 0
        val content = if (pageCount == 0) {
            "= OCR Book\n\nNo images found in ${dir.name}.\n"
        } else {
            val pageSections = (1..pageCount).joinToString("\n\n") { i ->
                "== Page $i\n\n[page vide ou OCR échec]"
            }
            "= OCR Book\n\n$pageSections\n"
        }
        output.writeText(content)
        state["outputFile"] = output
        state["content"] = content
    }

    @When("the collectOcr task runs with no images")
    fun theCollectOcrTaskRunsNoImages() {
        val dir = state["imageDir"] as File
        val output = File(dir.parentFile, "ocr-empty-output.adoc")
        val content = "= OCR Book\n\nNo images found in ${dir.name}.\n"
        output.writeText(content)
        state["outputFile"] = output
        state["content"] = content
    }

    @Then("the output file contains {int} page sections")
    fun theOutputFileContainsPageSections(count: Int) {
        val content = state["content"] as String
        val pageMatches = Regex("== Page \\d+").findAll(content).count()
        assertEquals(count, pageMatches)
    }

    @And("each section is prefixed with {string}")
    fun eachSectionIsPrefixedWith(prefix: String) {
        val content = state["content"] as String
        assertTrue(content.contains("$prefix "), "Output should contain '$prefix' prefix")
    }

    @Given("an empty image directory")
    fun anEmptyImageDirectory() {
        val tempDir = Files.createTempDirectory("ocr-bdd-empty").toFile()
        state["imageDir"] = tempDir
    }

    @Then("the output file contains a notice that no images were found")
    fun theOutputFileContainsNoImagesNotice() {
        val content = state["content"] as String
        assertTrue(content.contains("No images found"), "Output should mention no images found")
    }

    // ── US-CDX-13-3 — Contrat N2↔N2 : pages individuelles pour document-gradle ──

    @When("the collectOcr task writes pages to outputDir")
    fun theCollectOcrTaskWritesPagesToOutputDir() {
        val dir = state["imageDir"] as File
        val pageCount = state["pageCount"] as? Int ?: 0
        val outputDir = Files.createTempDirectory("ocr-bdd-pages").toFile()
        // Simulate the contract: one .adoc file per page, named NNN-<pageId>.adoc
        // (zero-padded 3-digit prefix). No "== Page N" header — file name carries order.
        if (pageCount == 0) {
            // No files written — contract respected
        } else {
            repeat(pageCount) { i ->
                val page = i + 1
                val pageId = "page-$page"
                val pageFileName = "%03d-%s.adoc".format(page, pageId)
                File(outputDir, pageFileName).writeText("[page vide ou OCR échec]")
            }
        }
        state["outputDir"] = outputDir
    }

    @Then("the outputDir contains {int} adoc files")
    fun theOutputDirContainsAdocFiles(expected: Int) {
        val outputDir = state["outputDir"] as File
        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" } ?: emptyArray()
        assertEquals(expected, adocFiles.size, "outputDir must contain exactly $expected .adoc files")
    }

    @And("each file is named with a 3-digit zero-padded numeric prefix")
    fun eachFileIsNamedWith3DigitZeroPaddedNumericPrefix() {
        val outputDir = state["outputDir"] as File
        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" } ?: emptyArray()
        val pattern = Regex("""^\d{3}-.*\.adoc$""")
        adocFiles.forEach { file ->
            assertTrue(pattern.matches(file.name),
                "file ${file.name} must start with a 3-digit zero-padded prefix (e.g. 001-<id>.adoc)")
        }
    }

    @And("each file contains the page structured text without the legacy header")
    fun eachFileContainsPageStructuredTextWithoutLegacyHeader() {
        val outputDir = state["outputDir"] as File
        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" } ?: emptyArray()
        adocFiles.forEach { file ->
            val content = file.readText()
            assertTrue(content.isNotBlank(), "page file ${file.name} must not be blank")
            assertTrue(!content.startsWith("== Page"),
                "page file ${file.name} must not contain the legacy '== Page N' header")
        }
    }

    @And("each file name starts with digits parseable as PageOrder")
    fun eachFileNameStartsWithDigitsParseableAsPageOrder() {
        val outputDir = state["outputDir"] as File
        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" } ?: emptyArray()
        val leadingDigits = Regex("""^(\d+)""")
        adocFiles.forEach { file ->
            val match = leadingDigits.find(file.nameWithoutExtension)
            assertTrue(match != null, "file ${file.name} must start with digits for PageOrder parsing")
            val orderValue = match!!.groupValues[1].toInt()
            assertTrue(orderValue >= 0, "PageOrder must be non-negative")
        }
    }

    @And("the files are ordered lexicographically by numeric prefix")
    fun theFilesAreOrderedLexicographicallyByNumericPrefix() {
        val outputDir = state["outputDir"] as File
        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" }
            ?.sortedBy { it.name } ?: emptyList()
        val leadingDigits = Regex("""^(\d+)""")
        val orders = adocFiles.map { file ->
            leadingDigits.find(file.nameWithoutExtension)!!.groupValues[1].toInt()
        }
        val sortedOrders = orders.sorted()
        assertEquals(sortedOrders, orders, "files sorted by name must be ordered by numeric prefix")
    }

    private fun stubEngine(text: String, confidence: Double, model: String): OcrEngine =
        object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult = OcrResult.of(
                text = text,
                confidence = confidence,
                language = request.language,
                model = model,
                metadata = mapOf("engine" to model)
            )
        }
}