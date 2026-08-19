package codex.tasks

import codex.enrichment.EnrichedLddNode
import codex.enrichment.GraphifyFileResolver
import codex.enrichment.JsonLddEnricher
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Enriches the JSON LDD structure with RAG chunks and Graphify knowledge
 * graph nodes.
 *
 * Consumes the LDD JSON produced by [AsciiDocToJsonLddTask], the chunks
 * JSON produced by [ChunkDocumentTask], and the `graph.json` produced by
 * graphify-gradle (N0). Calls [JsonLddEnricher.enrich] and writes the
 * resulting list of [EnrichedLddNode] as JSON for downstream consumers
 * (composite context, knowledge base documentation, DDD analysis).
 *
 * The Graphify graph file is optional — when missing or invalid, the
 * enricher degrades silently (no graphify nodes attached, no crash).
 *
 * @property jsonFile input JSON LDD file (output of `transformToJsonLdd`).
 * @property chunksFile input chunks JSON file (output of `transformChunk`).
 * @property graphifyFile input `graph.json` file (output of graphify-gradle).
 * @property outputFile output enriched JSON file.
 */
@DisableCachingByDefault(because = "LDD enrichment — deterministic pure computation, non-cacheable")
abstract class EnrichJsonLddTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jsonFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chunksFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val graphifyFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun enrich() {
        val lddInput = jsonFile.asFile.get()
        val chunksInput = chunksFile.asFile.get()
        val graphInput = graphifyFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] enrichJsonLdd : ${lddInput.name} + ${chunksInput.name} + ${graphInput.name} -> ${output.name}")

        val json = Json { ignoreUnknownKeys = true }
        val lddNodes = json.decodeFromString(ListSerializer(LddNode.serializer()), lddInput.readText())
        val ragChunks = json.decodeFromString(ListSerializer(DocumentChunk.serializer()), chunksInput.readText())

        val resolver = GraphifyFileResolver(graphInput)
        val enriched = JsonLddEnricher.enrich(lddNodes, ragChunks, resolver)

        val outputJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
        output.writeText(outputJson.encodeToString(ListSerializer(EnrichedLddNode.serializer()), enriched))

        logger.lifecycle(
            "[codex] enrichment : ${enriched.size} sections enriched, " +
                "${enriched.sumOf { it.ragChunks.size }} rag chunks attached, " +
                "${enriched.sumOf { it.graphifyNodes.size }} graphify nodes resolved"
        )
    }
}