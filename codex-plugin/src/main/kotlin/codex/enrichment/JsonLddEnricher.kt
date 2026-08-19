package codex.enrichment

import codex.tasks.DocumentChunk
import codex.tasks.LddNode
import kotlinx.serialization.Serializable

/**
 * Port for resolving Graphify nodes by section title.
 *
 * Implementations read the `graph.json` file produced by graphify-gradle
 * (N0) and return the ids of nodes whose label matches the given section
 * title. The contract is the JSON file — no compile dependency on
 * graphify-gradle.
 */
fun interface GraphifyResolver {
    /**
     * Returns the ids of Graphify nodes whose label matches [sectionTitle]
     * (case-insensitive). Empty list when no match.
     */
    fun resolve(sectionTitle: String): List<String>
}

/**
 * Enriched view of an [LddNode] with cross-source annotations.
 *
 * Computed by [JsonLddEnricher] from the JSON LDD structure, the RAG chunks
 * (pgvector) and the Graphify knowledge graph. The original [LddNode] is
 * preserved as [ldd] (composition — the source type is not modified).
 *
 * @property ldd the original LDD node (title, level, children, ...).
 * @property ragChunks RAG chunks whose sectionPath ends with this node title.
 * @property graphifyNodes ids of Graphify nodes resolved by title.
 * @property semanticDensity ratio `ragChunks.size / totalRagChunks`.
 * @property entities capitalized words extracted from title and text.
 */
@Serializable
data class EnrichedLddNode(
    val ldd: LddNode,
    val ragChunks: List<DocumentChunk>,
    val graphifyNodes: List<String>,
    val semanticDensity: Double,
    val entities: List<String>
)

/**
 * Pure object that enriches JSON LDD nodes with RAG chunks and Graphify nodes.
 *
 * Each section LDD node receives the RAG chunks whose `sectionPath` ends
 * with the section title (join by title), the Graphify node ids resolved
 * through [GraphifyResolver], a semantic density ratio, and capitalized
 * entities extracted from the title and text content.
 *
 * The enricher is deterministic and side-effect free: the same inputs
 * always yield the same output.
 */
object JsonLddEnricher {

    private val entityRegex = Regex("""\b[A-Z][A-Za-z0-9]+(?:\s+[A-Z][A-Za-z0-9]+)*\b""")

    /**
     * Enriches the given LDD nodes with RAG chunks and Graphify nodes.
     *
     * @param lddNodes the logical document description nodes (sections).
     * @param ragChunks all available RAG chunks (joined by section title).
     * @param graphifyResolver resolver for Graphify node ids by title.
     * @return an immutable list of [EnrichedLddNode], one per input node.
     */
    fun enrich(
        lddNodes: List<LddNode>,
        ragChunks: List<DocumentChunk>,
        graphifyResolver: GraphifyResolver
    ): List<EnrichedLddNode> {
        if (lddNodes.isEmpty()) return emptyList()
        val total = ragChunks.size
        return lddNodes.map { node ->
            val joined = if (node.title.isBlank()) emptyList()
            else ragChunks.filter { matchesSection(it.sectionPath, node.title) }
            val graphifyNodes = if (node.title.isBlank()) emptyList()
            else graphifyResolver.resolve(node.title)
            val density = if (total == 0) 0.0 else joined.size.toDouble() / total.toDouble()
            val entities = extractEntities(node)
            EnrichedLddNode(
                ldd = node,
                ragChunks = joined,
                graphifyNodes = graphifyNodes,
                semanticDensity = density,
                entities = entities
            )
        }
    }

    private fun matchesSection(sectionPath: String, title: String): Boolean {
        val trimmed = sectionPath.trim()
        if (trimmed.isEmpty()) return false
        val last = trimmed.substringAfterLast('>').trim()
        return last.equals(title, ignoreCase = true)
    }

    private fun extractEntities(node: LddNode): List<String> {
        val source = buildString {
            if (node.title.isNotBlank()) append(node.title).append(' ')
            if (!node.text.isNullOrBlank()) append(node.text)
        }
        return entityRegex.findAll(source)
            .map { it.value }
            .distinct()
            .toList()
    }
}