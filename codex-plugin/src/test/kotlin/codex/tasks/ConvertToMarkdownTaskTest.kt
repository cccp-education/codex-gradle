package codex.tasks

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [ConvertToMarkdownTask].
 *
 * First 10 tests (lines ~16-273) exercise the converter on hand-crafted AsciiDoc
 * fragments — one construct per test.
 *
 * CDX-7-2 chained tests (session 090) exercise the *real* output of
 * [ExtractBookStructureTask] fed into [ConvertToMarkdownTask], asserting the
 * extraction → conversion pipeline end-to-end on synthetic PDFs (bold/normal/mono
 * fonts). Baby-step TDD strict RED → GREEN → REFACTOR (GREEN: both tasks already
 * exist — characterization tests of the existing chained behavior).
 */
class ConvertToMarkdownTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `converts headers to markdown hash format`() {
        val adocFile = File(tempDir, "headers.adoc")
        val mdFile = File(tempDir, "headers.md")
        adocFile.writeText(
            """
            = Main Title
            
            == Chapter One
            
            Some intro text.
            
            === Section 1.1
            
            Section content.
            
            ==== Deep section
            
            == Chapter Two
            
            Final text.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        assertTrue(mdFile.exists())
        val md = mdFile.readText()
        assertTrue(md.startsWith("# "), "Should start with h1, got: ${md.lines().first()}")
        assertTrue(md.contains("## Chapter One"), "Should have h2")
        assertTrue(md.contains("### Section 1.1"), "Should have h3")
        assertTrue(md.contains("#### Deep section"), "Should have h4")
        assertTrue(md.contains("## Chapter Two"), "Should have second chapter")
    }

    @Test
    fun `preserves code blocks with language tag`() {
        val adocFile = File(tempDir, "code.adoc")
        val mdFile = File(tempDir, "code.md")
        adocFile.writeText(
            """
            = Code Example
            
            [source,java]
            ----
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            ----
            
            Some follow-up text.
            
            [source,kotlin]
            ----
            fun main() {
                println("Kotlin")
            }
            ----
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("System.out.println"), "Should preserve java code as text")
        assertTrue(md.contains("println(\"Kotlin\")"), "Should preserve kotlin code as text")
        assertTrue(md.contains("Some follow-up text."), "Should preserve text after code block")
    }

    @Test
    fun `converts admonitions to blockquotes`() {
        val adocFile = File(tempDir, "notes.adoc")
        val mdFile = File(tempDir, "notes.md")
        adocFile.writeText(
            """
            = Notes
            
            [NOTE]
            ====
            This is important information.
            ====
            
            [WARNING]
            ====
            Be careful with this setting.
            ====
            
            [TIP]
            ====
            Try using the shortcut Ctrl+S.
            ====
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("> **Note**"), "Should have note admonition")
        assertTrue(md.contains("> **Warning**"), "Should have warning admonition")
        assertTrue(md.contains("> **Tip**"), "Should have tip admonition")
    }

    @Test
    fun `converts table markup to markdown`() {
        val adocFile = File(tempDir, "table.adoc")
        val mdFile = File(tempDir, "table.md")
        adocFile.writeText(
            """
            = Table Test
            
            .Table 1. Comparison
            |===
            | Name | Value | Notes
            | foo  | 42    | first
            | bar  | 99    | second
            |===
            
            After the table.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("| Name | Value | Notes |"), "Should have table header")
        assertTrue(md.contains("| foo | 42 | first |"), "Should have table row")
        assertTrue(md.contains("After the table."), "Should preserve text after table")
    }

    @Test
    fun `converts images to markdown syntax`() {
        val adocFile = File(tempDir, "images.adoc")
        val mdFile = File(tempDir, "images.md")
        adocFile.writeText(
            """
            = Images
            
            image:screenshot.png[Screenshot of the application]
            
            Some text.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("![screenshot.png](Screenshot of the application)"), "Should convert image markdown")
    }

    @Test
    fun `converts links to markdown`() {
        val adocFile = File(tempDir, "links.adoc")
        val mdFile = File(tempDir, "links.md")
        adocFile.writeText(
            """
            = Links
            
            Check https://example.com for more info.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("https://example.com"), "Should preserve URL in output")
    }

    @Test
    fun `converts list items`() {
        val adocFile = File(tempDir, "list.adoc")
        val mdFile = File(tempDir, "list.md")
        adocFile.writeText(
            """
            = Lists
            
            * First item
            * Second item
            * Third item
            
            Normal text after list.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("- First item"), "Should convert list item")
        assertTrue(md.contains("- Second item"))
        assertTrue(md.contains("- Third item"))
        assertTrue(md.contains("Normal text after list."))
    }

    @Test
    fun `preserves inline formatting`() {
        val adocFile = File(tempDir, "inline.adoc")
        val mdFile = File(tempDir, "inline.md")
        adocFile.writeText(
            """
            = Formatting
            
            This text has **bold** and *italic* and `code` inline.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("**bold**"), "Should preserve bold")
        assertTrue(md.contains("*italic*"), "Should preserve italic")
        assertTrue(md.contains("`code`"), "Should preserve code")
    }

    @Test
    fun `skips comment lines`() {
        val adocFile = File(tempDir, "comments.adoc")
        val mdFile = File(tempDir, "comments.md")
        adocFile.writeText(
            """
            = Doc
            
            // This is a comment that should be skipped
            
            Visible content.
            
            // Another comment
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertFalse(md.contains("comment"), "Should not contain comments, got: $md")
        assertTrue(md.contains("Visible content"), "Should preserve visible content")
    }

    @Test
    fun `empty adoc produces minimal markdown`() {
        val adocFile = File(tempDir, "empty.adoc")
        val mdFile = File(tempDir, "empty.md")
        adocFile.writeText("= Empty\n")

        val task = createTask(adocFile, mdFile)
        task.convert()

        assertTrue(mdFile.exists())
        val md = mdFile.readText()
        assertTrue(md.length > 0, "Should produce non-empty output")
    }

    private fun createTask(adocFile: File, mdFile: File): ConvertToMarkdownTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "transformToMarkdown",
            ConvertToMarkdownTask::class.java
        ).get()
        task.adocFile.set(adocFile)
        task.markdownFile.set(mdFile)
        return task
    }

    // ── CDX-7-2 — Chained ExtractBookStructureTask → ConvertToMarkdownTask ──
    // Real extraction output (AsciiDoc from a synthetic PDF) is fed to the
    // converter instead of hand-crafted fragments. Validates the pipeline.

    private fun createExtractTask(pdfFile: File, outputFile: File): ExtractBookStructureTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "collectBookStructure",
            ExtractBookStructureTask::class.java
        ).get()
        task.pdfFile.set(pdfFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun createSyntheticBookPdf(file: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val normalFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val monoFont = PDType1Font(Standard14Fonts.FontName.COURIER)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(boldFont, 20f)
                cs.newLineAtOffset(50f, 720f)
                cs.showText("Introduction to Programming")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 16f)
                cs.newLineAtOffset(50f, 695f)
                cs.showText("Getting Started")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 670f)
                cs.showText("This chapter introduces programming concepts to beginners.")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 14f)
                cs.newLineAtOffset(50f, 645f)
                cs.showText("Hello World Example")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 620f)
                cs.showText("Let us write our first program.")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 595f)
                cs.showText("function main() {")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 582f)
                cs.showText("    println(\"Hello, World!\")")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 569f)
                cs.showText("}")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 544f)
                cs.showText("The function keyword declares a new function.")
                cs.endText()
            }
            doc.save(file)
        }
    }

    private fun runChainedPipeline(pdfFile: File, mdFile: File): String {
        val adocFile = File(tempDir, pdfFile.nameWithoutExtension + ".adoc")
        val extractTask = createExtractTask(pdfFile, adocFile)
        extractTask.extract()
        val convertTask = createTask(adocFile, mdFile)
        convertTask.convert()
        return mdFile.readText()
    }

    @Test
    fun `chained pipeline produces non-empty markdown from synthetic book pdf`() {
        val pdfFile = File(tempDir, "book.pdf")
        val mdFile = File(tempDir, "book.md")
        createSyntheticBookPdf(pdfFile)

        val md = runChainedPipeline(pdfFile, mdFile)

        assertTrue(mdFile.exists(), "Markdown file should exist after chained pipeline")
        assertTrue(md.isNotBlank(), "Chained markdown should be non-blank, got: $md")
    }

    @Test
    fun `chained pipeline preserves title as markdown h1`() {
        val pdfFile = File(tempDir, "title.pdf")
        val mdFile = File(tempDir, "title.md")
        createSyntheticBookPdf(pdfFile)

        val md = runChainedPipeline(pdfFile, mdFile)

        assertTrue(
            md.contains("# Introduction to Programming"),
            "Chained output should preserve the extracted title as h1, got: $md"
        )
    }

    @Test
    fun `chained pipeline preserves section hierarchy from extraction`() {
        val pdfFile = File(tempDir, "hierarchy.pdf")
        val mdFile = File(tempDir, "hierarchy.md")
        createSyntheticBookPdf(pdfFile)

        val md = runChainedPipeline(pdfFile, mdFile)

        assertTrue(md.contains("Getting Started"), "Should preserve section heading text")
        assertTrue(md.contains("Hello World Example"), "Should preserve sub-section heading text")
        assertTrue(
            md.contains("### Getting Started") || md.contains("## Getting Started"),
            "Getting Started should map to a markdown heading, got: $md"
        )
        assertTrue(
            md.contains("#### Hello World Example") || md.contains("### Hello World Example"),
            "Hello World Example should map to a deeper markdown heading, got: $md"
        )
    }

    @Test
    fun `chained pipeline preserves body paragraphs from extraction`() {
        val pdfFile = File(tempDir, "body.pdf")
        val mdFile = File(tempDir, "body.md")
        createSyntheticBookPdf(pdfFile)

        val md = runChainedPipeline(pdfFile, mdFile)

        assertTrue(
            md.contains("This chapter introduces programming concepts to beginners."),
            "Should preserve first body paragraph from extraction"
        )
        assertTrue(
            md.contains("Let us write our first program."),
            "Should preserve second body paragraph from extraction"
        )
        assertTrue(
            md.contains("The function keyword declares a new function."),
            "Should preserve trailing body paragraph from extraction"
        )
    }

    @Test
    fun `chained pipeline preserves code lines from mono-font extraction`() {
        val pdfFile = File(tempDir, "code.pdf")
        val mdFile = File(tempDir, "code.md")
        createSyntheticBookPdf(pdfFile)

        val md = runChainedPipeline(pdfFile, mdFile)

        assertTrue(
            md.contains("function main()"),
            "Should preserve first code line extracted from mono font, got: $md"
        )
        assertTrue(
            md.contains("println"),
            "Should preserve println code line extracted from mono font, got: $md"
        )
    }

    @Test
    fun `chained pipeline on empty pdf produces minimal markdown`() {
        val emptyPdf = File(tempDir, "empty.pdf")
        val mdFile = File(tempDir, "empty.md")
        PDDocument().use { it.save(emptyPdf) }

        val md = runChainedPipeline(emptyPdf, mdFile)

        assertTrue(mdFile.exists(), "Markdown file should exist even for empty PDF")
        assertTrue(md.isNotBlank(), "Chained markdown for empty PDF should be non-blank")
        assertTrue(
            md.contains("Document vide") || md.contains("Empty document"),
            "Should preserve empty-document marker from extraction, got: $md"
        )
    }
}
