package codex.tasks

import codebase.store.DocumentChunk
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * SÉQUENCE-C C-2 — characterization of the `chunks.json` JSON contract.
 *
 * The producer ([ChunkDocumentTask], codex N2) writes `chunks.json`; the
 * consumer (`CodexIngestTask`, via the N1 store) reads it. This suite pins
 * the contract that any `chunks.json` written by the producer is readable as
 * `List<codebase.store.DocumentChunk>` with every field preserved.
 *
 * It is a characterization test: GREEN before the C-2 unification (the codex
 * duplicate and the N1 store type serialize identically) and GREEN after the
 * switch (a single N1 type). It guards backward compatibility of existing
 * `chunks.json` artefacts on disk.
 */
class ChunkDocumentJsonContractTest {

    @TempDir
    lateinit var tempDir: File

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `producer output decodes as the N1 store type with all fields preserved`() {
        val mdFile = File(tempDir, "contract.md")
        val chunksFile = File(tempDir, "chunks.json")
        mdFile.writeText(
            """
            # Part One

            Intro paragraph with a sentence.

            ## Chapter A

            Chapter content with a sentence.

            ```kotlin
            val x = 1
            ```
            """.trimIndent()
        )

        createTask(mdFile, chunksFile, "Apache-2.0").chunk()

        val chunks = json.decodeFromString(
            ListSerializer(DocumentChunk.serializer()),
            chunksFile.readText()
        )

        assertTrue(chunks.size >= 2, "expected the producer sections, got ${chunks.size}")
        val chapter = chunks.first { it.sectionPath.contains("Chapter A") }
        assertEquals("contract", chapter.sourceDocument)
        assertEquals("Part One > Chapter A", chapter.sectionPath)
        assertEquals(2, chapter.headingLevel)
        assertTrue(chapter.content.contains("Chapter content"))
        assertEquals(listOf("val x = 1"), chapter.codeBlocks)
        assertTrue(chapter.entities.isEmpty())
        assertEquals("Apache-2.0", chapter.license)
    }

    @Test
    fun `producer and store types serialize byte-identically`() {
        val mdFile = File(tempDir, "bytes.md")
        val chunksFile = File(tempDir, "chunks.json")
        mdFile.writeText(
            """
            # Bytes

            Content of the section.

            ## Next

            Next content.
            """.trimIndent()
        )

        createTask(mdFile, chunksFile, "UNKNOWN").chunk()
        val written = json.decodeFromString(
            ListSerializer(DocumentChunk.serializer()),
            chunksFile.readText()
        )

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val producerJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        val reEncoded = producerJson.encodeToString(
            ListSerializer(DocumentChunk.serializer()),
            written
        )

        assertEquals(
            chunksFile.readText(),
            reEncoded,
            "chunks.json must round-trip through the store type with the producer's settings"
        )
    }

    @Test
    fun `defaults and null overlap survive the store type`() {
        val mdFile = File(tempDir, "defaults.md")
        val chunksFile = File(tempDir, "chunks.json")
        mdFile.writeText(
            """
            # Only Section

            Final content, no next section.
            """.trimIndent()
        )

        createTask(mdFile, chunksFile, "UNKNOWN").chunk()

        val chunk = json.decodeFromString(
            ListSerializer(DocumentChunk.serializer()),
            chunksFile.readText()
        ).single()

        assertEquals(null, chunk.overlapNext, "last section keeps null overlap through the store type")
        assertTrue(chunk.entities.isEmpty())
        assertEquals("UNKNOWN", chunk.license)
    }

    private fun createTask(
        mdFile: File,
        chunksFile: File,
        license: String
    ): ChunkDocumentTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "transformChunk",
            ChunkDocumentTask::class.java
        ).get()
        task.markdownFile.set(mdFile)
        task.chunksFile.set(chunksFile)
        task.licenseName.set(license)
        return task
    }
}
