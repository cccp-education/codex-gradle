package codex.tasks

import codex.CodexExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/**
 * US-CDX-13-3 — Contrat N2↔N2 : collectOcr produit des pages individuelles.
 *
 * Baby-step TDD RED → GREEN → REFACTOR.
 *
 * Le pont N2 (codex) ↔ N2 (document-gradle) est le filesystem. document-gradle
 * `BookAssembler` attend un répertoire de fichiers `NNN-<pageId>.adoc` (contrat
 * `PageOrder.fromFileName` — leading digits déterminent l'ordre). Avant cette
 * US, `CollectOcrTask` ne produisait qu'un seul fichier concaténé, brisant le
 * pont N2↔N2 spécifié par `BookPage` KDoc.
 *
 * Cette suite valide le nouveau contrat `outputDir` :
 * - Chaque page OCR est écrite dans un fichier individuel `NNN-<pageId>.adoc`
 * - Le numéro de page est zero-padded sur 3 digits (001, 002, ...)
 * - L'ordre lexicographique des noms de fichiers correspond à l'ordre des pages
 * - Le contenu de chaque fichier est le `structuredText` de l'OcrResult
 *   (sans header `== Page N` — le header est porté par le nom de fichier)
 */
class CollectOcrPagesContractTest {

    @Test
    fun `task exposes an outputDir directory property`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        assertNotNull(task.outputDir, "outputDir property must exist")
        assertTrue(task.outputDir is org.gradle.api.file.DirectoryProperty,
            "outputDir must be a DirectoryProperty")
    }

    @Test
    fun `collectOcr writes one adoc file per page in outputDir with zero-padded numeric prefix`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        // 3 minimal PNG files (1x1 transparent PNG header — enough for listImageFiles)
        File(imagesDir, "page-001.png").writeBytes(minimalPng())
        File(imagesDir, "page-002.png").writeBytes(minimalPng())
        File(imagesDir, "page-003.png").writeBytes(minimalPng())

        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        // Force both OCR engines to fail so we get the fallback "[page vide ou OCR échec]"
        // text — avoids any network call in the test (no Ollama, no Tesseract binary).
        task.ollamaHost.set("localhost")
        task.ollamaPort.set("1") // closed port → immediate connection refused
        task.model.set("gpt-oss:120b-cloud")
        task.language.set("fr")

        task.collectOcr()

        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" }
            ?.sortedBy { it.name } ?: emptyList()
        assertEquals(3, adocFiles.size, "exactly one .adoc file per image must be written")

        // Zero-padded 3-digit prefix → lexicographic order matches page order
        assertEquals("001-page-001.adoc", adocFiles[0].name, "page 1 file name must be zero-padded")
        assertEquals("002-page-002.adoc", adocFiles[1].name, "page 2 file name must be zero-padded")
        assertEquals("003-page-003.adoc", adocFiles[2].name, "page 3 file name must be zero-padded")

        // Each file contains the structuredText (no "== Page N" header — the
        // header is now carried by the file name, as BookPage/PageOrder expects)
        adocFiles.forEach { pageFile ->
            val content = pageFile.readText()
            assertTrue(content.isNotBlank(), "page file ${pageFile.name} must not be blank")
            assertTrue(!content.startsWith("== Page"),
                "page file must not contain the legacy '== Page N' header (now carried by file name)")
        }
    }

    @Test
    fun `collectOcr outputDir pages are consumable by document BookPage contract`(
        @TempDir tempDir: File
    ) {
        // This test asserts the N2↔N2 bridge is aligned: the file names produced
        // by codex `collectOcr` must be parseable by document-gradle `PageOrder.fromFileName`.
        // PageOrder.fromFileName extracts leading digits → the 001, 002, 003 prefixes
        // here must yield PageOrder(1), PageOrder(2), PageOrder(3).
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "scan-a.png").writeBytes(minimalPng())
        File(imagesDir, "scan-b.png").writeBytes(minimalPng())

        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.ollamaHost.set("localhost")
        task.ollamaPort.set("1")
        task.model.set("gpt-oss:120b-cloud")
        task.language.set("fr")

        task.collectOcr()

        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" }
            ?.sortedBy { it.name } ?: emptyList()
        assertEquals(2, adocFiles.size)

        // Leading digits prefix → PageOrder-compatible
        val leadingDigits = Regex("""^(\d+)""")
        adocFiles.forEach { file ->
            val match = leadingDigits.find(file.nameWithoutExtension)
            assertNotNull(match, "file name ${file.name} must start with digits for PageOrder")
            val orderValue = match!!.groupValues[1].toInt()
            assertTrue(orderValue >= 1, "PageOrder must be non-negative (PageOrder requires >= 0)")
        }
    }

    @Test
    fun `collectOcr writes no files in outputDir when no images found`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val emptyDir = File(tempDir, "empty").apply { mkdirs() }
        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(emptyDir)
        task.outputDir.set(outputDir)
        task.ollamaHost.set("localhost")
        task.ollamaPort.set("1")
        task.model.set("gpt-oss:120b-cloud")
        task.language.set("fr")

        task.collectOcr()

        val adocFiles = outputDir.listFiles { f -> f.isFile && f.extension == "adoc" } ?: emptyArray()
        assertEquals(0, adocFiles.size, "no .adoc files must be written when no images are present")
    }

    private fun minimalPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk header
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1 pixel
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), // RGBA, CRC
        0x89.toByte(), // IDAT start
    )
}