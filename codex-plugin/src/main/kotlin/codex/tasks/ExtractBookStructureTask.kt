package codex.tasks

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "PDFBox extraction + font analysis — external process, non-cacheable")
abstract class ExtractBookStructureTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pdfFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = pdfFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] collectBookStructure : ${input.name} → ${output.name}")

        val adocContent = buildAsciiDoc(input)

        output.writeText(adocContent)
        logger.lifecycle(
            "[codex] ✓ Structure extraite — ${adocContent.lines().size} lignes AsciiDoc"
        )
    }

    private fun buildAsciiDoc(pdfFile: java.io.File): String {
        val positionedLines = extractTextPositions(pdfFile)

        if (positionedLines.isEmpty()) return "= [Document vide]\n\n"

        val nonCode = positionedLines.filter { it.fontStyle != FontStyle.MONOSPACE }
        val sizes = nonCode.map { it.fontSize.toDouble() }
        val avg: Double = sizes.average()
        val max: Double = (sizes.maxOrNull() ?: avg)
        val min: Double = (sizes.minOrNull() ?: avg)
        val range = max - min

        val headerThresholds = computeHeaderThresholds(avg, max, min, range)

        logger.lifecycle(
            "[codex] Fontes: avg=${"%.1f".format(avg)}, max=${"%.1f".format(max)}, " +
                "h1≥${"%.1f".format(headerThresholds.h1)}, h2≥${"%.1f".format(headerThresholds.h2)}, " +
                "h3≥${"%.1f".format(headerThresholds.h3)}, h4≥${"%.1f".format(headerThresholds.h4)}"
        )

        val groups = groupTextPositionsByY(positionedLines)

        return buildAsciiDocFromGroups(groups, headerThresholds)
    }

    private data class HeaderThresholds(
        val h1: Double, val h2: Double, val h3: Double, val h4: Double
    )

    private fun computeHeaderThresholds(
        avg: Double, max: Double, min: Double, range: Double
    ): HeaderThresholds {
        val dynamicH1 = if (range > 1.0) max - range * 0.05 else max * 0.95
        val dynamicH2 = if (range > 2.0) max - range * 0.25 else max * 0.75
        val dynamicH3 = if (range > 3.0) max - range * 0.50 else max * 0.50
        val dynamicH4 = if (range > 4.0) max - range * 0.70 else max * 0.35
        return HeaderThresholds(dynamicH1, dynamicH2, dynamicH3, dynamicH4)
    }

    data class PositionedLine(
        val text: String,
        val fontSize: Float,
        val fontStyle: FontStyle,
        val y: Float
    )

    private fun extractTextPositions(pdfFile: java.io.File): List<PositionedLine> {
        val builder = StringBuilder()
        val lines = mutableListOf<PositionedLine>()

        Loader.loadPDF(pdfFile).use { document ->
            val stripper = object : PDFTextStripper() {
                override fun writeString(
                    text: String,
                    textPositions: List<TextPosition>
                ) {
                    if (textPositions.isEmpty()) return
                    val first = textPositions.first()
                    val style = FontStyleDetector.detect(first.font.name)
                    val line = PositionedLine(
                        text = text.trimEnd(),
                        fontSize = first.fontSize,
                        fontStyle = style,
                        y = first.y
                    )
                    if (line.text.isNotBlank()) {
                        lines.add(line)
                    }
                }
            }
            stripper.sortByPosition = true
            stripper.getText(document)
        }

        return lines
    }

    private data class TextGroup(
        val lines: MutableList<PositionedLine> = mutableListOf()
    )

    private fun groupTextPositionsByY(lines: List<PositionedLine>): List<TextGroup> {
        val groups = mutableListOf<TextGroup>()
        var current: TextGroup? = null
        var lastY = -1f

        for (line in lines.sortedBy { it.y }) {
            val isSameLine = kotlin.math.abs(line.y - lastY) < 2f
            if (current == null || !isSameLine) {
                current = TextGroup()
                groups.add(current)
            }
            current.lines.add(line)
            lastY = line.y
        }
        return groups
    }

    private fun buildAsciiDocFromGroups(
        groups: List<TextGroup>,
        thresholds: HeaderThresholds
    ): String {
        val sb = StringBuilder()
        var codeBuffer = mutableListOf<String>()
        var inCodeBlock = false

        fun flushCodeBlock() {
            if (codeBuffer.isNotEmpty()) {
                sb.appendLine("[source,text]")
                sb.appendLine("----")
                codeBuffer.forEach { sb.appendLine(it) }
                sb.appendLine("----")
                sb.appendLine()
                codeBuffer = mutableListOf()
                inCodeBlock = false
            }
        }

        for ((idx, group) in groups.withIndex()) {
            val first = group.lines.first()
            val allMonospace = group.lines.all { it.fontStyle == FontStyle.MONOSPACE }
            val isHeading = !allMonospace && group.lines.size <= 3

            if (allMonospace && first.fontSize <= 14f) {
                if (!inCodeBlock) {
                    flushCodeBlock()
                    sb.appendLine("[source,text]")
                    sb.appendLine("----")
                    inCodeBlock = true
                }
                group.lines.forEach { sb.appendLine(it.text.trimEnd()) }
                continue
            }

            if (inCodeBlock) {
                flushCodeBlock()
            }

            if (isHeading && group.lines.size <= 2) {
                val fullText = group.lines.joinToString(" ") { it.text.trim() }
                val bold = group.lines.all { it.fontStyle.isBold() || it.fontStyle == FontStyle.NORMAL }
                val sizeBoost = if (bold) 0.5 else 0.0
                val adjustedSize = first.fontSize + sizeBoost.toFloat()

                val prefix = when {
                    adjustedSize >= thresholds.h1 -> "= "
                    adjustedSize >= thresholds.h2 -> "== "
                    adjustedSize >= thresholds.h3 -> "=== "
                    adjustedSize >= thresholds.h4 -> "==== "
                    else -> null
                }

                if (prefix != null) {
                    if (idx > 0 && sb.isNotEmpty() && !sb.endsWith("\n\n"))
                        sb.appendLine()
                    sb.appendLine("$prefix${fullText.removePrefix("= ").removePrefix("== ").removePrefix("=== ").removePrefix("==== ")}")
                    sb.appendLine()
                    continue
                }
            }

            val paraText = group.lines.joinToString(" ") { it.text.trim() }
                .replace(Regex("""\s+"""), " ")
            if (paraText.isNotBlank()) {
                if (idx > 0 && sb.isNotEmpty() && !sb.endsWith("\n\n"))
                    sb.append(" ")
                sb.append(paraText)
                if (idx < groups.size - 1) sb.appendLine()
            }
        }

        flushCodeBlock()
        return sb.toString().trimEnd() + "\n"
    }
}
