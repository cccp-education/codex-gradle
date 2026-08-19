package codex.ontology

import codex.tasks.LddNode

/**
 * Pure object that derives an [OntologyMapping] from a SQL LMD script
 * and the LDD nodes of the source document.
 *
 * Three archetypal queries (P1-19 spec) are computed from the DDL:
 *
 *  - **Bounded contexts** — connected components of the FK graph.
 *  - **Aggregates** — tables referenced via `ON DELETE CASCADE` and
 *    their dependent entities.
 *  - **Value objects** — tables without outgoing `REFERENCES` clauses.
 *
 * The deriver is deterministic and side-effect free: the same inputs
 * always yield the same [OntologyMapping].
 */
object OntologyDeriver {

    private val createTableRegex =
        Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\((.*?)\)\s*;""", RegexOption.DOT_MATCHES_ALL)

    private val fkRegex =
        Regex("""(\w+)\s+\w+.*?REFERENCES\s+(\w+)\s*\(\w+\)(?:\s+ON\s+DELETE\s+CASCADE)?""", RegexOption.IGNORE_CASE)

    private val fkWithCascadeRegex =
        Regex("""(\w+)\s+\w+.*?REFERENCES\s+(\w+)\s*\(\w+\)\s+ON\s+DELETE\s+CASCADE""", RegexOption.IGNORE_CASE)

    private val columnDefRegex =
        Regex("""^\s*(\w+)\s+""")

    /**
     * Derives the ontological mapping from the given LDD nodes and SQL
     * LMD script.
     *
     * @param lddNodes the logical document description nodes (used for
     *        naming bounded contexts from section titles).
     * @param sqlScript the DDL/INSERT script produced by
     *        `ImportBookSqlTask`.
     * @return an immutable [OntologyMapping] with the three computed
     *         views.
     */
    fun derive(lddNodes: List<LddNode>, sqlScript: String): OntologyMapping {
        val tables = parseTables(sqlScript)
        if (tables.isEmpty()) {
            return OntologyMapping(emptyList(), emptyList(), emptyList())
        }
        val outgoingFks = parseOutgoingFks(sqlScript)
        val cascadeRoots = parseCascadeRoots(sqlScript)
        val boundedContexts = computeBoundedContexts(tables, outgoingFks, lddNodes)
        val aggregates = computeAggregates(tables, cascadeRoots)
        val valueObjects = computeValueObjects(tables, outgoingFks)
        return OntologyMapping(boundedContexts, aggregates, valueObjects)
    }

    private fun parseTables(sql: String): Map<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        for (match in createTableRegex.findAll(sql)) {
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            val columns = body.split(",")
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("--")) return@mapNotNull null
                    columnDefRegex.find(trimmed)?.groupValues?.get(1)
                        ?.takeIf { it.isNotBlank() }
                }
                .filter { it != "PRIMARY" && it != "FOREIGN" && it != "UNIQUE" && it != "CHECK" }
            result[name] = columns
        }
        return result
    }

    private fun parseOutgoingFks(sql: String): Map<String, List<String>> {
        val outgoing = linkedMapOf<String, MutableList<String>>()
        for (match in createTableRegex.findAll(sql)) {
            val table = match.groupValues[1]
            val body = match.groupValues[2]
            val targets = fkRegex.findAll(body)
                .map { it.groupValues[2] }
                .distinct()
                .toList()
            if (targets.isNotEmpty()) {
                outgoing.getOrPut(table) { mutableListOf() }.addAll(targets)
            }
        }
        return outgoing
    }

    private data class CascadeEdge(val from: String, val to: String)

    private fun parseCascadeRoots(sql: String): List<CascadeEdge> {
        val edges = mutableListOf<CascadeEdge>()
        for (match in createTableRegex.findAll(sql)) {
            val fromTable = match.groupValues[1]
            val body = match.groupValues[2]
            for (fkMatch in fkWithCascadeRegex.findAll(body)) {
                val toTable = fkMatch.groupValues[2]
                if (fromTable != toTable) {
                    edges.add(CascadeEdge(from = fromTable, to = toTable))
                }
            }
        }
        return edges
    }

    private fun computeBoundedContexts(
        tables: Map<String, List<String>>,
        outgoingFks: Map<String, List<String>>,
        lddNodes: List<LddNode>
    ): List<BoundedContext> {
        val tableNames = tables.keys
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        for (t in tableNames) adjacency.getOrPut(t) { mutableSetOf() }
        for ((from, targets) in outgoingFks) {
            for (to in targets) {
                adjacency.getOrPut(from) { mutableSetOf() }.add(to)
                adjacency.getOrPut(to) { mutableSetOf() }.add(from)
            }
        }
        val visited = mutableSetOf<String>()
        val contexts = mutableListOf<BoundedContext>()
        val contextName = lddNodes.firstOrNull { it.level > 0 }?.title?.takeIf { it.isNotBlank() }
        var contextIndex = 0
        for (table in tableNames) {
            if (table in visited) continue
            val component = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(table)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current in visited) continue
                visited.add(current)
                component.add(current)
                for (neighbor in adjacency[current] ?: emptySet()) {
                    if (neighbor !in visited) queue.add(neighbor)
                }
            }
            val name = contextName ?: "bc_${contextIndex + 1}"
            contexts.add(BoundedContext(name = name, tables = component.sorted()))
            contextIndex++
        }
        return contexts
    }

    private fun computeAggregates(
        tables: Map<String, List<String>>,
        cascadeEdges: List<CascadeEdge>
    ): List<Aggregate> {
        val dependentsByRoot = mutableMapOf<String, MutableSet<String>>()
        for (edge in cascadeEdges) {
            dependentsByRoot.getOrPut(edge.to) { mutableSetOf() }.add(edge.from)
        }
        return dependentsByRoot.entries
            .filter { it.key in tables }
            .map { (root, deps) -> Aggregate(rootTable = root, dependentTables = deps.sorted()) }
            .sortedBy { it.rootTable }
    }

    private fun computeValueObjects(
        tables: Map<String, List<String>>,
        outgoingFks: Map<String, List<String>>
    ): List<ValueObject> {
        return tables.entries
            .filter { it.key !in outgoingFks }
            .map { (table, columns) -> ValueObject(table = table, columns = columns) }
    }
}