package codex.enrichment

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Adapter that reads a `graph.json` file (Graphify N0 contract) and
 * resolves node ids by label.
 *
 * No compile dependency on graphify-gradle — the contract is the JSON file
 * shape produced by `GraphModel`:
 *
 * ```
 * { "nodes": [ { "id": "...", "label": "...", "type": "..." }, ... ],
 *   "edges": [...], "communities": [...] }
 * ```
 *
 * Missing fields are tolerated (degraded to empty results) so that a
 * partial or empty graph does not crash the enrichment pipeline.
 */
class GraphifyFileResolver(private val graphJsonFile: File) : GraphifyResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun resolve(sectionTitle: String): List<String> {
        if (!graphJsonFile.exists()) return emptyList()
        val model = runCatching { json.decodeFromString(GraphFileModel.serializer(), graphJsonFile.readText()) }
            .getOrNull() ?: return emptyList()
        return model.nodes
            .filter { it.label.equals(sectionTitle, ignoreCase = true) }
            .map { it.id }
    }
}

@Serializable
private data class GraphFileModel(
    val nodes: List<GraphFileNode> = emptyList(),
    val edges: List<GraphFileEdge> = emptyList(),
    val communities: List<GraphFileCommunity> = emptyList()
)

@Serializable
private data class GraphFileNode(
    val id: String,
    val label: String,
    val type: String = "",
    val community: String? = null
)

@Serializable
private data class GraphFileEdge(
    val source: String = "",
    val target: String = "",
    val type: String = "",
    val label: String? = null
)

@Serializable
private data class GraphFileCommunity(
    val id: String = "",
    val label: String = "",
    val size: Int = 0
)