package codex.tasks

import java.security.MessageDigest

/**
 * Pure semantic chunker — splits a Markdown document into [DocumentChunk]
 * sections by heading. Extracted from `ChunkDocumentTask` (CDX-7-3,
 * constat #7 of `CADRAGE_EPIC_P1_CORPUS_ENRICHMENT`).
 *
 * Object with no I/O, no Gradle — unit-testable in isolation. The chunking
 * algorithm (heading detection, hierarchy path, code block extraction,
 * overlap with next section, deterministic SHA-256 ids) is preserved
 * verbatim from `ChunkDocumentTask` so `ChunkDocumentTask` can delegate
 * to this object without behaviour change.
 *
 * This object is the single canonical chunking implementation : both
 * `ChunkDocumentTask` and `CodexPipelineTask` delegate to it (the
 * former since CDX-7-3, the latter since EPIC CDX-UNIFY).
 */
object SemanticChunker {

    /**
     * A parsed heading section : heading line, numeric level, title, and
     * accumulated content lines. Public so tests can construct sections
     * directly for `buildSectionPath` verification.
     */
    data class Section(
        val headingLine: String,
        val headingLevel: Int,
        val headingText: String,
        val contentLines: MutableList<String> = mutableListOf()
    )

    /**
     * Splits [text] Markdown into semantic chunks by heading.
     *
     * One chunk per heading section. Each chunk contains the heading,
     * its body content, extracted code blocks, and an overlap of the
     * next section's first two sentences for retrieval context continuity.
     *
     * @param text Markdown content to chunk
     * @param sourceDocument name of the source document (used in chunk ids + sectionPath)
     * @param license license tag to apply to all chunks
     */
    fun chunk(text: String, sourceDocument: String, license: String): List<DocumentChunk> =
        buildChunks(text, sourceDocument, license)

    /**
     * Builds a hierarchical section path for the section at [currentIndex]
     * by walking ancestors with a strictly lower heading level.
     */
    fun buildSectionPath(sections: List<Section>, currentIndex: Int): String {
        val path = mutableListOf<String>()
        val target = sections[currentIndex]

        path.add(0, target.headingText)

        for (j in (currentIndex - 1) downTo 0) {
            val ancestor = sections[j]
            if (ancestor.headingLevel < target.headingLevel && ancestor.headingLevel > 0) {
                path.add(0, ancestor.headingText)
                if (ancestor.headingLevel == 1) break
            }
        }

        return path.joinToString(" > ")
    }

    /**
     * Extracts the first two sentences from the first non-heading,
     * non-code-fence lines of [lines]. Returns `null` when no usable
     * text is found.
     */
    fun extractFirstTwoSentences(lines: List<String>): String? {
        val textLines = lines.filter { l ->
            val t = l.trim()
            !t.startsWith("#") && !t.startsWith("```") && t.isNotBlank()
        }
        if (textLines.isEmpty()) return null

        val combined = textLines.take(3).joinToString(" ")
        val sentences = combined.split(Regex("(?<=[.!?])\\s+"))
        val result = sentences.take(2).joinToString(" ").trim()
        return result.ifBlank { null }
    }

    private fun buildChunks(text: String, sourceDocument: String, license: String): List<DocumentChunk> {
        val lines = text.lines()
        val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")

        val sections = mutableListOf<Section>()
        var currentSection: Section? = null
        val pendingLines = mutableListOf<String>()

        for (line in lines) {
            val match = headingPattern.find(line)
            if (match != null) {
                if (currentSection != null) {
                    mergePendingIntoContent(pendingLines, currentSection)
                    sections.add(currentSection)
                }
                val level = match.groupValues[1].length
                val title = match.groupValues[2].trim()
                currentSection = Section(
                    headingLine = line,
                    headingLevel = level,
                    headingText = title
                )
                pendingLines.clear()
            } else {
                pendingLines.add(line)
            }
        }

        if (currentSection != null) {
            mergePendingIntoContent(pendingLines, currentSection)
            sections.add(currentSection)
        } else if (pendingLines.isNotEmpty()) {
            sections.add(
                Section(
                    headingLine = "",
                    headingLevel = 0,
                    headingText = sourceDocument,
                    contentLines = pendingLines.toMutableList()
                )
            )
        }

        val chunks = mutableListOf<DocumentChunk>()

        for (i in sections.indices) {
            val section = sections[i]
            val content = buildContent(section)
            if (content.isBlank()) continue

            val codeBlocks = extractCodeBlocks(section.contentLines)
            val sectionPath = buildSectionPath(sections, i)

            val nextContent = if (i + 1 < sections.size) {
                extractFirstTwoSentences(sections[i + 1].contentLines)
            } else null

            val id = generateChunkId(sourceDocument, sectionPath)

            chunks.add(
                DocumentChunk(
                    id = id,
                    sourceDocument = sourceDocument,
                    sectionPath = sectionPath,
                    headingLevel = section.headingLevel,
                    content = content,
                    codeBlocks = codeBlocks,
                    entities = emptyList(),
                    overlapNext = nextContent,
                    license = license
                )
            )
        }

        return chunks
    }

    private fun mergePendingIntoContent(pendingLines: MutableList<String>, section: Section) {
        val trimmed = trimLeadingAndTrailingBlanks(pendingLines)
        section.contentLines.addAll(trimmed)
    }

    private fun trimLeadingAndTrailingBlanks(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.isNotBlank() }
        val end = lines.indexOfLast { it.isNotBlank() }
        if (start == -1) return emptyList()
        return lines.subList(start, end + 1)
    }

    private fun buildContent(section: Section): String {
        val lines = mutableListOf<String>()
        lines.add(section.headingLine)
        section.contentLines.forEach { lines.add(it) }
        return lines.joinToString("\n").trim()
    }

    private fun extractCodeBlocks(lines: List<String>): List<String> {
        val blocks = mutableListOf<String>()
        var inCode = false
        val currentBlock = StringBuilder()

        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks.add(currentBlock.toString().trimEnd())
                    currentBlock.clear()
                }
                inCode = !inCode
            } else if (inCode) {
                currentBlock.appendLine(line)
            }
        }
        return blocks
    }

    private fun generateChunkId(source: String, sectionPath: String): String {
        val input = "$source:$sectionPath"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray()).take(8).joinToString("") {
            "%02x".format(it)
        }
        return "chk-$hash"
    }
}