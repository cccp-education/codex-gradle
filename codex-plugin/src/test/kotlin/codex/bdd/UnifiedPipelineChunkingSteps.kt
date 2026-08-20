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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * Cucumber steps for `codex_pipeline_unified_chunking.feature` (CDX-UNIFY-2).
 *
 * Drives [codex.tasks.CodexPipelineTask] end-to-end through Gradle's
 * `ProjectBuilder`, validating the pipeline output contract after the
 * `chunkMd` → `SemanticChunker.chunk()` unification (constat #7).
 *
 * Step phrases are prefixed with "unified" to avoid DuplicateStepDefinition
 * collisions with [LicencePipelineRoutingSteps] (CDX-6-3) which drives the
 * same task with different phrasing.
 *
 * Pure BDD — no production code is modified by this suite. The unification
 * was implemented in CDX-UNIFY-1 (refactor delegation).
 */
class UnifiedPipelineChunkingSteps {

    private lateinit var tempDir: File
    private lateinit var pdfFile: File
    private var baseDir: File? = null
    private var routingEnabled: Boolean = false
    private var fallbackZone: LicenseZone = LicenseZone.UNKNOWN
    private var outputFile: File? = null
    private var actualOutput: File? = null
    private var secondOutput: File? = null

    @Given("a unified pipeline source PDF containing the text {string}")
    fun aUnifiedPipelineSourcePdfContainingTheText(text: String) {
        tempDir = kotlin.io.path.createTempDirectory(prefix = "cdx-unify-bdd-").toFile()
        pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, text)
    }

    @And("a unified pipeline base directory {string} under a temporary folder")
    fun aUnifiedPipelineBaseDirectoryUnderTemporaryFolder(name: String) {
        baseDir = File(tempDir, name)
    }

    @And("the unified pipeline is configured with licence routing enabled and fallback zone {string}")
    fun theUnifiedPipelineIsConfiguredWithRoutingEnabledAndFallbackZone(zoneName: String) {
        routingEnabled = true
        fallbackZone = parseZone(zoneName)
    }

    @And("the unified pipeline is configured with licence routing disabled")
    fun theUnifiedPipelineIsConfiguredWithRoutingDisabled() {
        routingEnabled = false
    }

    @When("the unified transform corpus to pdf pipeline runs")
    fun theUnifiedTransformCorpusToPdfPipelineRuns() {
        actualOutput = runPipeline()
    }

    @When("the unified transform corpus to pdf pipeline runs twice on the same input")
    fun theUnifiedTransformCorpusToPdfPipelineRunsTwiceOnTheSameInput() {
        actualOutput = runPipeline(suffix = "run1")
        secondOutput = runPipeline(suffix = "run2")
    }

    @Then("the unified pipeline output is written under zone {string}")
    fun theUnifiedPipelineOutputIsWrittenUnderZone(zoneName: String) {
        val root = baseDir ?: error("baseDir not configured for routing scenario")
        val dir = File(root, zoneName)
        assertTrue(dir.isDirectory, "Expected directory $zoneName to exist under $root")
        val output = actualOutput ?: error("No output file found")
        assertTrue(
            output.absolutePath.startsWith(dir.absolutePath),
            "Output $output should be under $dir"
        )
    }

    @Then("the unified pipeline output is written to the configured output file")
    fun theUnifiedPipelineOutputIsWrittenToTheConfiguredOutputFile() {
        val expected = outputFile ?: error("outputFile not configured for non-routing scenario")
        assertTrue(expected.exists(), "Configured output file $expected should exist")
        val output = actualOutput ?: error("No output file found")
        assertTrue(
            output.absolutePath == expected.absolutePath,
            "Output $output should be the configured outputFile $expected"
        )
    }

    @And("the unified pipeline output file has non-blank AsciiDoc content")
    fun theUnifiedPipelineOutputFileHasNonBlankAsciiDocContent() {
        val output = actualOutput ?: error("No output file found")
        val content = output.readText()
        assertTrue(content.isNotBlank(), "Output content should not be blank")
        assertTrue(
            content.trimStart().startsWith("="),
            "Output should be AsciiDoc (starts with '=')"
        )
    }

    @Then("the two unified pipeline outputs are identical")
    fun theTwoUnifiedPipelineOutputsAreIdentical() {
        val first = actualOutput ?: error("First output not found")
        val second = secondOutput ?: error("Second output not found")
        assertTrue(first.exists() && second.exists(), "Both outputs should exist")
        assertEquals(
            first.readText(),
            second.readText(),
            "Pipeline outputs should be identical across two runs on the same input"
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun runPipeline(suffix: String = ""): File {
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

        return if (routingEnabled) {
            task.licenceRouting.set(true)
            task.baseDir.set(baseDir)
            task.fallbackZone.set(fallbackZone)
            task.outputFile.set(File(tempDir, "placeholder$suffix.adoc"))
            task.pipeline()
            val root = baseDir ?: error("baseDir not set for routing scenario")
            root.walkTopDown().first { it.isFile && it.extension == "adoc" }
        } else {
            task.licenceRouting.set(false)
            val out = File(tempDir, "build/output$suffix.adoc")
            out.parentFile.mkdirs()
            task.outputFile.set(out)
            outputFile = out
            task.pipeline()
            out
        }
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