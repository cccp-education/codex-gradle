package codex.tasks

import codebase.store.DocumentChunk
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
 * Splits a Markdown document into semantic chunks by heading.
 *
 * One chunk per heading section. Each chunk contains the heading,
 * its body content, extracted code blocks, and an overlap of the
 * next section's first two sentences for retrieval context continuity.
 *
 * The chunk type is the N1 store type `codebase.store.DocumentChunk`
 * (SÉQUENCE-C C-2 — single type; the former codex-local duplicate was
 * removed so the future `pages` field is maintained in one place only).
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
