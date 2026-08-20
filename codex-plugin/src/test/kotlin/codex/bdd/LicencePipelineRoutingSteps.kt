package codex.bdd

import codex.LicenseZone
import codex.tasks.CodexPipelineTask
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * Cucumber steps for `codex_licence_routing.feature` (CDX-6-3).
 *
 * Drives [codex.tasks.CodexPipelineTask] end-to-end through Gradle's
 * `ProjectBuilder` — mirrors the functional test
 * [codex.tasks.CodexPipelineRoutingTest] but expressed as BDD scenarios.
 * No production code is modified; this is pure behaviour validation of
 * the routing wired in CDX-6-1/CDX-6-2.
 */
class LicencePipelineRoutingSteps {

    private lateinit var tempDir: File
    private lateinit var pdfFile: File
    private var baseDir: File? = null
    private var routingEnabled: Boolean = false
    private var fallbackZone: LicenseZone = LicenseZone.UNKNOWN
    private var outputFile: File? = null
    private var actualOutput: File? = null

    @Given("a pipeline source PDF containing the text {string}")
    fun aPipelineSourcePdfContainingTheText(text: String) {
        tempDir = kotlin.io.path.createTempDirectory(prefix = "cdx-routing-bdd-").toFile()
        pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, text)
    }

    @And("a pipeline base directory {string} under a temporary folder")
    fun aPipelineBaseDirectoryUnderTemporaryFolder(name: String) {
        baseDir = File(tempDir, name)
    }

    @And("the codex pipeline is configured with licence routing enabled and fallback zone {string}")
    fun theCodexPipelineIsConfiguredWithRoutingEnabledAndFallbackZone(zoneName: String) {
        routingEnabled = true
        fallbackZone = parseZone(zoneName)
    }

    @And("the codex pipeline is configured with licence routing disabled")
    fun theCodexPipelineIsConfiguredWithRoutingDisabled() {
        routingEnabled = false
    }

    @When("the transform corpus to pdf pipeline runs")
    fun theTransformCorpusToPdfPipelineRuns() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "transformCorpusToPdf",
            CodexPipelineTask::class.java
        ).get()
        task.sourceFile.set(pdfFile)
        task.pgHost.set("localhost")
        task.pgPort.set("5432")
        task.pgDatabase.set("codex")
        task.pgUser.set("codex")
        task.pgPassword.set("codex")

        if (routingEnabled) {
            task.licenceRouting.set(true)
            task.baseDir.set(baseDir)
            task.fallbackZone.set(fallbackZone)
            task.outputFile.set(File(tempDir, "placeholder.adoc"))
        } else {
            task.licenceRouting.set(false)
            val out = File(tempDir, "build/output.adoc")
            out.parentFile.mkdirs()
            task.outputFile.set(out)
            outputFile = out
        }
        task.pipeline()
        actualOutput = findOutputFile()
    }

    @Then("the pipeline output is written under zone {string}")
    fun thePipelineOutputIsWrittenUnderZone(zoneName: String) {
        val root = baseDir ?: error("baseDir not configured for routing scenario")
        val dir = File(root, zoneName)
        assertTrue(dir.isDirectory, "Expected directory $zoneName to exist under $root")
        val output = actualOutput ?: error("No output file found")
        assertTrue(output.absolutePath.startsWith(dir.absolutePath),
            "Output $output should be under $dir")
    }

    @Then("the pipeline output is written to the configured output file")
    fun thePipelineOutputIsWrittenToTheConfiguredOutputFile() {
        val expected = outputFile ?: error("outputFile not configured for non-routing scenario")
        assertTrue(expected.exists(), "Configured output file $expected should exist")
        val output = actualOutput ?: error("No output file found")
        assertTrue(output.absolutePath == expected.absolutePath,
            "Output $output should be the configured outputFile $expected")
    }

    @And("the pipeline output file has a non-blank content")
    fun thePipelineOutputFileHasNonBlankContent() {
        val output = actualOutput ?: error("No output file found")
        assertTrue(output.readText().isNotBlank(), "Output content should not be blank")
    }

    private fun findOutputFile(): File {
        if (!routingEnabled) {
            return outputFile ?: error("outputFile not set for non-routing scenario")
        }
        val root = baseDir ?: error("baseDir not set for routing scenario")
        return root.walkTopDown().first { it.isFile && it.extension == "adoc" }
    }

    private fun parseZone(name: String): LicenseZone = when (name.uppercase()) {
        "OSS" -> LicenseZone.OSS
        "CSS" -> LicenseZone.CSS
        else -> LicenseZone.UNKNOWN
    }

    private fun createPdf(file: File, text: String) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(font, 11f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText(text)
                cs.endText()
            }
            doc.save(file)
        }
    }
}