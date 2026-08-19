package codex.bdd

import codex.LicenseZone
import codex.tasks.DeployKnowledgeBaseRoutedTask
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
 * Cucumber steps for `codex_export_routed.feature` (CDX-5-3).
 *
 * Drives [codex.tasks.DeployKnowledgeBaseRoutedTask] end-to-end through
 * Gradle's `ProjectBuilder` — mirrors the functional test
 * [codex.tasks.DeployKnowledgeBaseRoutedTaskTest] but expressed as BDD
 * scenarios. No production code is exercised beyond the task wiring
 * already validated in S-082; this is pure behaviour validation.
 */
class LicenceRoutingSteps {

    private lateinit var tempDir: File
    private lateinit var chunksFile: File
    private lateinit var baseDir: File
    private lateinit var pdfFile: File
    private var fallbackZone: LicenseZone = LicenseZone.UNKNOWN
    private var exportedRoot: File? = null

    @Given("a knowledge base chunks file with source {string} and license {string}")
    fun aKnowledgeBaseChunksFileWithSourceAndLicense(source: String, license: String) {
        tempDir = kotlin.io.path.createTempDirectory(prefix = "cdx-licence-bdd-").toFile()
        chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks(source, license))
    }

    @And("a base directory {string} under a temporary folder")
    fun aBaseDirectoryUnderTemporaryFolder(name: String) {
        baseDir = File(tempDir, name)
    }

    @Given("a PDF containing the text {string}")
    fun aPdfContainingTheText(text: String) {
        pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, text)
    }

    @When("the deploy knowledge base routed task runs with fallback zone {string}")
    fun theDeployKnowledgeBaseRoutedTaskRunsWithFallbackZone(zoneName: String) {
        fallbackZone = parseZone(zoneName)
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "deployKnowledgeBaseRouted",
            DeployKnowledgeBaseRoutedTask::class.java
        ).get()
        task.chunksFile.set(chunksFile)
        task.pdfFile.set(pdfFile)
        task.baseDir.set(baseDir)
        task.fallbackZone.set(fallbackZone)
        task.export()
        exportedRoot = baseDir
    }

    @Then("the knowledge base is exported under {string}")
    fun theKnowledgeBaseIsExportedUnder(relativePath: String) {
        val root = exportedRoot ?: error("Task did not run")
        val dir = File(root, relativePath)
        assertTrue(dir.isDirectory, "Expected directory $relativePath to exist under $root")
    }

    @And("the knowledge base contains a {string} file")
    fun theKnowledgeBaseContainsAFile(fileName: String) {
        val root = exportedRoot ?: error("Task did not run")
        val found = root.walkTopDown().any { it.name == fileName && it.isFile }
        assertTrue(found, "Expected a $fileName file somewhere under $root")
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

    private fun simpleChunks(source: String, license: String): String {
        return """
        [
          {
            "id": "chk-1",
            "sourceDocument": "$source",
            "sectionPath": "Title",
            "headingLevel": 1,
            "content": "# Title\n\nBody.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          }
        ]
        """.trimIndent()
    }
}