package codex.enrichment

/**
 * Pure object that builds the Graphify channel content of the
 * [contracts.context.CompositeContext] from the enriched LDD nodes.
 *
 * Each enriched section carrying at least one Graphify node id is
 * rendered as a block containing the section title and the list of
 * resolved Graphify node ids. Sections without Graphify nodes are
 * omitted (ink economy — only what the knowledge graph actually
 * covers enters the composite context).
 *
 * The builder is deterministic and side-effect free: the same input
 * always yields the same output. An empty input list yields an empty
 * string, preserving backward compatibility with the previous
 * `graphifySection = ""` default.
 *
 * Only [EnrichedLddNode.graphifyNodes] is consumed — rag chunks and
 * entities belong to other channels and must not leak into the
 * Graphify section.
 */
object GraphifySectionBuilder {

    /**
     * Builds the Graphify channel text from the enriched LDD nodes.
     *
     * @param enrichedNodes the enriched LDD nodes produced by [JsonLddEnricher].
     * @return the Graphify channel text, or an empty string when no
     *         section carries any Graphify node.
     */
    fun build(enrichedNodes: List<EnrichedLddNode>): String {
        val sections = enrichedNodes.filter { it.graphifyNodes.isNotEmpty() }
        if (sections.isEmpty()) return ""
        return buildString {
            sections.forEachIndexed { index, node ->
                if (index > 0) append("\n\n")
                append(node.ldd.title)
                append('\n')
                node.graphifyNodes.forEach { nodeId ->
                    append("- ")
                    append(nodeId)
                    append('\n')
                }
            }
        }.trimEnd()
    }
}