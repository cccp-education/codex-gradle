package codex.tasks

import codex.CodexExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class CollectOcrTaskTest {

    @Test
    fun `plugin registers collectOcr task with correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr")
        assertNotNull(task, "collectOcr should be registered")
        assertTrue(task is CollectOcrTask)
    }

    @Test
    fun `collectOcr task has correct group and description`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr")
        assertNotNull(task)
        assertEquals("collect", task!!.group)
        assertTrue(task.description!!.contains("OCR"), "Description should mention OCR")
        assertTrue(task.description!!.contains("DOC-11"), "Description should mention DOC-11")
    }

    @Test
    fun `collectOcr task inherits language convention from extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        assertEquals("fr", task.language.get())
    }

    @Test
    fun `collectOcr task produces empty output when no images found`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val emptyDir = Files.createTempDirectory("ocr-empty-functional").toFile()
        val outputFile = File(emptyDir.parentFile, "ocr-output.adoc")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(emptyDir)
        task.outputFile.set(outputFile)
        task.language.set("fr")

        task.collectOcr()

        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        assertTrue(content.contains("No images found"), "Output should mention no images found")
    }

    @Test
    fun `extension exposes ocr language property`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        assertEquals("fr", extension.ocrLanguage.get())
    }
}