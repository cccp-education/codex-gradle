package codex.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * A single semantic chunk extracted from a Markdown document.
 *
 * Each chunk corresponds to one heading section with its content,
 * optional overlap hint for the next section, and any code blocks found.
 *
 * @property id deterministic SHA-256 based identifier (e.g. "chk-a1b2c3d4")
 * @property sourceDocument name of the source document file
 * @property sectionPath hierarchical section path (e.g. "Chapter 1 > Section 1.2")
 * @property headingLevel heading depth (1-6)
 * @property content full section content including heading line
 * @property codeBlocks extracted fenced code blocks as strings
 * @property entities named entity references (placeholder for future extraction)
 * @property overlapNext first two sentences of the following section for context continuity
 * @property license license tag for this chunk (Apache-2.0 / PROPRIETARY / UNKNOWN)
 */
@Serializable
data class DocumentChunk(
    val id: String,
    val sourceDocument: String,
    val sectionPath: String,
    val headingLevel: Int,
    val content: String,
    val codeBlocks: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val overlapNext: String? = null,
    val license: String = "UNKNOWN"
)

/**
 * Splits a Markdown document into semantic chunks by heading.
 *
 * One chunk per heading section. Each chunk contains the heading,
 * its body content, extracted code blocks, and an overlap of the
 * next section's first two sentences for retrieval context continuity.
 *
 * @property markdownFile input Markdown file
 * @property chunksFile output JSON file containing the list of [DocumentChunk]
 * @property licenseName license tag to apply to all chunks
 */
@DisableCachingByDefault(because = "Markdown chunking — pure computation, non-cacheable")
abstract class ChunkDocumentTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val markdownFile: RegularFileProperty

    @get:OutputFile
    abstract val chunksFile: RegularFileProperty

    @get:Input
    abstract val licenseName: Property<String>

    @TaskAction
    fun chunk() {
        val input = markdownFile.asFile.get()
        val output = chunksFile.asFile.get()

        logger.lifecycle("[codex] transformChunk : ${input.name} → ${output.name}")

        val sourceDocument = input.nameWithoutExtension
        val text = input.readText()
        val license = licenseName.get()
        logger.lifecycle("[codex]   License: $license")
        val chunks = SemanticChunker.chunk(text, sourceDocument, license)

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        output.writeText(json.encodeToString(chunks))

        logger.lifecycle(
            "[codex] ✓ Chunking done — ${chunks.size} chunks produced " +
                "(${chunks.map { it.content.lines().size }.sum()} total lines)"
        )
    }
}
