package codex.tasks

import codex.LicenseZone
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeployKnowledgeBaseRoutedTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `apache license pdf routes to OSS directory`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0.")

        val chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks("apabook", "Apache-2.0"))
        val baseDir = File(tempDir, "kb")

        val task = createTask(chunksFile, pdfFile, baseDir, LicenseZone.UNKNOWN)
        task.export()

        val docDir = File(baseDir, "OSS/apabook")
        assertTrue(docDir.isDirectory, "OSS/apabook should exist")
        assertTrue(File(docDir, "knowledge-base.json").exists())
        assertTrue(File(docDir, "knowledge-base.md").exists())
        assertTrue(File(docDir, "knowledge-base.adoc").exists())
    }

    @Test
    fun `copyright pdf routes to office directory`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "© 2024 Acme Corp. All rights reserved.")

        val chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks("cssbook", "PROPRIETARY"))
        val baseDir = File(tempDir, "kb")

        val task = createTask(chunksFile, pdfFile, baseDir, LicenseZone.UNKNOWN)
        task.export()

        val docDir = File(baseDir, "office/cssbook")
        assertTrue(docDir.isDirectory, "office/cssbook should exist")
        assertTrue(File(docDir, "knowledge-base.json").exists())
    }

    @Test
    fun `unknown pdf falls back to configured OSS zone`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "No license mention here, just plain content.")

        val chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks("fallbackoss", "Apache-2.0"))
        val baseDir = File(tempDir, "kb")

        val task = createTask(chunksFile, pdfFile, baseDir, LicenseZone.OSS)
        task.export()

        val docDir = File(baseDir, "OSS/fallbackoss")
        assertTrue(docDir.isDirectory, "OSS/fallbackoss should exist via fallback")
        assertTrue(File(docDir, "knowledge-base.json").exists())
    }

    @Test
    fun `unknown pdf falls back to configured CSS zone`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Plain content with no license marker.")

        val chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks("fallbackcss", "PROPRIETARY"))
        val baseDir = File(tempDir, "kb")

        val task = createTask(chunksFile, pdfFile, baseDir, LicenseZone.CSS)
        task.export()

        val docDir = File(baseDir, "office/fallbackcss")
        assertTrue(docDir.isDirectory, "office/fallbackcss should exist via fallback")
        assertTrue(File(docDir, "knowledge-base.adoc").exists())
    }

    @Test
    fun `creative commons pdf routes to OSS directory`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "This work is licensed under Creative Commons CC-BY 4.0.")

        val chunksFile = File(tempDir, "chunks.json")
        chunksFile.writeText(simpleChunks("ccbook", "Apache-2.0"))
        val baseDir = File(tempDir, "kb")

        val task = createTask(chunksFile, pdfFile, baseDir, LicenseZone.UNKNOWN)
        task.export()

        val docDir = File(baseDir, "OSS/ccbook")
        assertTrue(docDir.isDirectory, "OSS/ccbook should exist for CC-BY")
        assertTrue(File(docDir, "knowledge-base.md").exists())
    }

    private fun createTask(
        chunksFile: File,
        pdfFile: File,
        baseDir: File,
        fallbackZone: LicenseZone
    ): DeployKnowledgeBaseRoutedTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "deployKnowledgeBaseRouted",
            DeployKnowledgeBaseRoutedTask::class.java
        ).get()
        task.chunksFile.set(chunksFile)
        task.pdfFile.set(pdfFile)
        task.baseDir.set(baseDir)
        task.fallbackZone.set(fallbackZone)
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