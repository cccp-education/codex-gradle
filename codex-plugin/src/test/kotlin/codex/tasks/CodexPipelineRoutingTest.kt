package codex.tasks

import codex.LicenseZone
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TDD — EPIC CDX-6-1 : `LicenseRouter` routing physique intégré à `CodexPipelineTask`.
 *
 * Avant CDX-6-1 : `CodexPipelineTask` écrit toujours dans `outputFile` (hardcoded,
 * généralement `build/codex/`). La licence est détectée par chemin projet
 * (`LicenseZoneDetector`), pas par contenu PDF.
 *
 * Après CDX-6-1 : la task expose `licenceRouting` (default false, backward compat),
 * `baseDir` (optional), `fallbackZone` (optional). Quand `licenceRouting = true`,
 * `PdfLicenseDetector` scanne le PDF source → `LicenseRouter` route la sortie
 * vers `baseDir/OSS/` ou `baseDir/office/`.
 *
 * Baby-step TDD strict : RED (propriétés/methodes inexistantes) → GREEN → REFACTOR.
 */
class CodexPipelineRoutingTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `task exposes licenceRouting property defaulting to false`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()

        assertFalse(task.licenceRouting.get(), "licenceRouting should default to false (backward compat)")
    }

    @Test
    fun `task exposes optional baseDir property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()

        assertFalse(task.baseDir.isPresent, "baseDir should be optional (no default)")
    }

    @Test
    fun `task exposes optional fallbackZone property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()

        assertFalse(task.fallbackZone.isPresent, "fallbackZone should be optional (no default)")
    }

    @Test
    fun `routing enabled with apache pdf writes output to OSS directory`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0.")
        val baseDir = File(tempDir, "routed")

        val task = createPipelineTask(pdfFile, baseDir, routing = true, fallback = LicenseZone.UNKNOWN)
        task.pipeline()

        val ossDir = File(baseDir, "OSS")
        assertTrue(ossDir.isDirectory, "OSS/ directory should exist when routing Apache PDF")
        val outputFiles = ossDir.listFiles()?.toList() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Output file should be written inside OSS/")
        assertTrue(outputFiles.any { it.readText().isNotBlank() }, "Output content should not be blank")
    }

    @Test
    fun `routing enabled with copyright pdf writes output to office directory`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "© 2024 Acme Corp. All rights reserved.")
        val baseDir = File(tempDir, "routed")

        val task = createPipelineTask(pdfFile, baseDir, routing = true, fallback = LicenseZone.UNKNOWN)
        task.pipeline()

        val officeDir = File(baseDir, "office")
        assertTrue(officeDir.isDirectory, "office/ directory should exist when routing copyright PDF")
        val outputFiles = officeDir.listFiles()?.toList() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Output file should be written inside office/")
    }

    @Test
    fun `routing disabled writes output to configured outputFile`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0.")
        val outputFile = File(tempDir, "build/output.adoc")
        outputFile.parentFile.mkdirs()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()
        task.sourceFile.set(pdfFile)
        task.outputFile.set(outputFile)
        task.pgHost.set("localhost")
        task.pgPort.set("5432")
        task.pgDatabase.set("codex")
        task.pgUser.set("codex")
        task.pgPassword.set("codex")
        // licenceRouting defaults to false — no routing, backward compat
        task.pipeline()

        assertTrue(outputFile.exists(), "Output should be written to configured outputFile when routing disabled")
        assertTrue(outputFile.readText().isNotBlank(), "Output content should not be blank")
    }

    @Test
    fun `routing enabled with unknown pdf falls back to configured zone`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "No license mention here, just plain content.")
        val baseDir = File(tempDir, "routed")

        val task = createPipelineTask(pdfFile, baseDir, routing = true, fallback = LicenseZone.OSS)
        task.pipeline()

        val ossDir = File(baseDir, "OSS")
        assertTrue(ossDir.isDirectory, "OSS/ should exist via fallback zone when PDF content is UNKNOWN")
        val outputFiles = ossDir.listFiles()?.toList() ?: emptyList()
        assertTrue(outputFiles.isNotEmpty(), "Output file should be written inside OSS/ via fallback")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createPipelineTask(
        pdfFile: File,
        baseDir: File,
        routing: Boolean,
        fallback: LicenseZone
    ): CodexPipelineTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()
        task.sourceFile.set(pdfFile)
        task.outputFile.set(File(tempDir, "placeholder.adoc"))
        task.pgHost.set("localhost")
        task.pgPort.set("5432")
        task.pgDatabase.set("codex")
        task.pgUser.set("codex")
        task.pgPassword.set("codex")
        task.licenceRouting.set(routing)
        task.baseDir.set(baseDir)
        task.fallbackZone.set(fallback)
        return task
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