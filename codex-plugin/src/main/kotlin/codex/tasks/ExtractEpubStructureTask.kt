package codex.tasks

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.ToXMLContentHandler
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.FileInputStream
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(because = "Apache Tika EPUB extraction — external process, non-cacheable")
abstract class ExtractEpubStructureTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val epubFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = epubFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] collectEpubStructure : ${input.name} → ${output.name}")

        val adocContent = buildAsciiDoc(input)
        output.writeText(adocContent)
        logger.lifecycle(
            "[codex] ✓ EPUB structure done — ${adocContent.lines().size} AsciiDoc lines"
        )
    }

    private fun buildAsciiDoc(epubFile: java.io.File): String {
        val xhtmlContent = extractXhtml(epubFile)
        val result = convertXhtmlToAsciiDoc(xhtmlContent)
        return result.ifBlank { "EPUB vide" }
    }

    private fun extractXhtml(file: java.io.File): String {
        val handler = ToXMLContentHandler()
        val parser = AutoDetectParser()
        FileInputStream(file).use { input ->
            parser.parse(input, handler, Metadata(), ParseContext())
        }
        return handler.toString()
    }

    private fun convertXhtmlToAsciiDoc(xhtml: String): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xhtml.byteInputStream())
        val sb = StringBuilder()

        traverse(doc.documentElement, sb, 0)
        return sb.toString()
    }

    private fun traverse(node: Node, sb: StringBuilder, depth: Int) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val el = node as Element
                when (el.tagName.lowercase()) {
                    "h1" -> sb.appendLine("= ${el.textContent.trim()}")
                    "h2" -> sb.appendLine("== ${el.textContent.trim()}")
                    "h3" -> sb.appendLine("=== ${el.textContent.trim()}")
                    "h4" -> sb.appendLine("==== ${el.textContent.trim()}")
                    "h5" -> sb.appendLine("===== ${el.textContent.trim()}")
                    "h6" -> sb.appendLine("====== ${el.textContent.trim()}")
                    "p" -> {
                        val content = buildInlineContent(el)
                        if (content.isNotBlank()) sb.appendLine(content)
                    }
                    "pre" -> {
                        val content = el.textContent.trim()
                        if (content.isNotBlank()) {
                            sb.appendLine("[source,text]")
                            sb.appendLine("----")
                            sb.appendLine(content)
                            sb.appendLine("----")
                        }
                    }
                    "ul", "ol" -> {
                        sb.appendLine()
                        for (child in el.childNodes()) {
                            if (child is Element && child.tagName.lowercase() == "li") {
                                val content = buildInlineContent(child)
                                sb.appendLine("* $content")
                            }
                        }
                        sb.appendLine()
                    }
                    "blockquote" -> {
                        for (child in el.childNodes()) {
                            if (child is Element && child.tagName.lowercase() == "p") {
                                sb.appendLine("[NOTE]")
                                sb.appendLine("==== ${buildInlineContent(child)}")
                                sb.appendLine()
                            }
                        }
                    }
                    else -> {
                        for (child in el.childNodes()) traverse(child, sb, depth + 1)
                    }
                }
            }
        }
    }

    private fun buildInlineContent(element: Element): String {
        val sb = StringBuilder()
        for (child in element.childNodes()) {
            when (child.nodeType) {
                Node.TEXT_NODE -> sb.append(child.textContent)
                Node.ELEMENT_NODE -> {
                    val el = child as Element
                    when (el.tagName.lowercase()) {
                        "em", "i" -> sb.append("_${el.textContent}_")
                        "strong", "b" -> sb.append("*${el.textContent}*")
                        "code" -> sb.append("``${el.textContent}``")
                        "a" -> {
                            val href = el.getAttribute("href")
                            sb.append("${el.textContent}[$href]")
                        }
                        "br" -> sb.append(" +\n")
                        "span" -> sb.append(el.textContent)
                        else -> sb.append(el.textContent)
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    private fun Element.childNodes(): List<Node> =
        (0 until this.childNodes.length).map { this.childNodes.item(it) }

    private fun Node.childNodes(): List<Node> =
        (0 until this.childNodes.length).map { this.childNodes.item(it) }
}
