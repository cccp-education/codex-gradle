package codex.tasks

import contracts.context.ChannelBudget
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import contracts.context.ContextChannel
import codebase.store.DoubtExposure
import codebase.store.RagVectorStore
import codebase.store.RetrieveResult
import codex.Metadata
import codex.enrichment.EnrichedLddNode
import codex.enrichment.GraphifySectionBuilder
import codex.provenance.PageProvenanceReport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "pgvector RAG + LLM call — external dependencies, non-cacheable")
abstract class CodexCompositeContextTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val query: Property<String>

    @get:Input
    @get:Optional
    abstract val topK: Property<String>

    // CDX-CR3-2: pg properties exposed and wired from CodexExtension.
    // Before fix, the task instantiated the store with localhost:5432 hardcoded.
    // EPIC CDX-RAG-3: delegates to the codebase.store.RagVectorStore socle
    // (single N2->N1 edge, zero cycle).
    @get:Input abstract val pgHost: Property<String>
    @get:Input abstract val pgPort: Property<String>
    @get:Input abstract val pgDatabase: Property<String>
    @get:Input abstract val pgUser: Property<String>
    @get:Input abstract val pgPassword: Property<String>

    init {
        pgHost.convention("localhost")
        pgPort.convention("5432")
        pgDatabase.convention("codex")
        pgUser.convention("codex")
        pgPassword.convention("codex")
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    // CDX-DOUBT-BRIDGE-2 : when true, chunks flagged doubtful by the OCR
    // policy are dropped from the Docs channel instead of annotated (mirror
    // the N1 `CompositeContextBuilder.excludeDoubtfulDocs`). Default false —
    // backward compat : doubtful chunks stay, annotated with the marker.
    @get:Input
    @get:Optional
    abstract val excludeDoubtfulDocs: Property<Boolean>

    // CDX-4-3 : canal Graphify peuplé depuis le JSON enrichi produit par
    // `enrichJsonLdd` (List<EnrichedLddNode> sérialisée).
    // CDX-CONTEXT-HARDENING-1 : collection de fichiers tolérante (et non
    // `@InputFile`) — le wiring plugin cible l'artefact par défaut, mais son
    // absence n'échoue plus la validation Gradle. `generateCompositeContext`
    // s'exécute standalone (fallback `""`, Économie d'Encre — jamais forcer
    // `enrichJsonLdd`) tout en peuplant le canal Graphify quand l'artefact
    // existe.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val enrichedJsonFile: ConfigurableFileCollection

    /**
     * Test seam — overrides the RAG store so the full [execute] chain can be
     * driven without pgvector/Docker. Unset in production (the task builds a
     * real [RagVectorStore] from the pg properties).
     */
    @get:Internal
    abstract val storeOverride: Property<RagVectorStore>

    /**
     * CDX-PAGE-PROVENANCE-3 — page provenance sidecar
     * (`page-provenance.json`, a [PageProvenanceReport]). Joined back to the
     * retrieval results so each composite entry localises its source page.
     *
     * `@InputFiles` tolerant (pattern CDX-CONTEXT-HARDENING S-221): the artefact
     * is targeted by default, its absence degrades to no `pages` field
     * (backward compatible JSON, Économie d'Encre — never force
     * `collectPageProvenance`).
     *
     * Join key is `sectionPath` (not `chunkId`): the retrieval result's
     * `chunkId` is the pgvector BIGSERIAL id (`Long`) whereas the sidecar keys
     * on the semantic SHA-256 chunk id; the only shared identity is
     * `sourceDocument + sectionPath` (cadrage D6 deviation, S-224).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pageProvenanceFile: ConfigurableFileCollection

    /**
     * Loads the optional page provenance sidecar into a
     * `sectionPath → pages` index. Absent, empty or invalid → empty map
     * (degraded silent, backward compat with the pre-provenance JSON).
     */
    internal fun buildPageIndex(): Map<String, List<Int>> {
        val file = pageProvenanceFile.singleOrNull() ?: return emptyMap()
        if (!file.exists()) return emptyMap()
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val report = json.decodeFromString(PageProvenanceReport.serializer(), file.readText())
            report.chunks
                .filter { it.pages.isNotEmpty() }
                .associate { it.sectionPath to it.pages }
        } catch (e: Exception) {
            logger.warn("[codex] pageProvenance : sidecar unreadable ({}), fallback to no pages", e.message)
            emptyMap()
        }
    }

    /**
     * Builds the Graphify channel section text from the enriched LDD
     * JSON file. Returns an empty string when the file is absent, empty,
     * or invalid (degraded silent — backward compat with the previous
     * `graphifySection = ""` default).
     */
    internal fun buildGraphifySection(): String {
        val file = enrichedJsonFile.singleOrNull() ?: return ""
        if (!file.exists()) return ""
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val nodes = json.decodeFromString(
                ListSerializer(EnrichedLddNode.serializer()),
                file.readText()
            )
            GraphifySectionBuilder.build(nodes)
        } catch (e: Exception) {
            logger.warn("[codex] graphifySection : enriched JSON unreadable ({}), fallback to empty", e.message)
            ""
        }
    }

    /**
     * Doubt-aware retrieval seam — delegates to the N1 socle
     * [RagVectorStore.searchWithDoubt] so the SELECT reads the additive
     * `confidence` / `doubtful` columns (CDX-DOUBT-BRIDGE-2).
     *
     * `internal` so the delegation is verifiable with a recording fake
     * (no Docker); the real R2DBC path is covered by the integration suite.
     */
    internal suspend fun searchDoubtAware(
        store: RagVectorStore,
        query: String,
        topK: Int,
    ): List<RetrieveResult> = store.searchWithDoubt(query, topK)

    /**
     * Composes the Docs channel text from the doubt-aware results, honoring
     * the socle exposure policy [DoubtExposure]: doubtful chunks are
     * annotated with the marker + confidence by default, or dropped when
     * [excludeDoubtful] is true (mirror `CompositeContextBuilder` N1).
     *
     * Provenance (`[source / sectionPath]`, similarity) is preserved on top
     * of the socle-rendered line so the N3 consumer keeps its citation
     * context while the doubt policy stays owned by N1.
     */
    internal fun buildDocsContent(results: List<RetrieveResult>, excludeDoubtful: Boolean): String {
        val lines = DoubtExposure.expose(results, excludeDoubtful = excludeDoubtful)
        val retained = if (excludeDoubtful) results.filterNot { it.doubtful } else results
        return retained.zip(lines).joinToString("\n\n") { (r, line) ->
            "[${r.sourceDocument} / ${r.sectionPath}] (similarity=${"%.3f".format(r.similarity)})\n$line"
        }
    }

    /**
     * Serialization seam for the N3 `composite-context.json` contract.
     *
     * kotlinx.serialization has no serializer for `Any` — writing
     * `Map<String, Any>` directly threw `Serializer for class 'Any' is not
     * found` at execution time (discovered by the S-220 dogfooding run). The
     * neighbouring [ExportKnowledgeBaseTask] already builds JSON with
     * [JsonObject]/[JsonPrimitive]; this seam mirrors that pattern so the
     * contract is serializable and unit-testable without a database.
     */
    internal fun buildCompositeJson(
        results: List<RetrieveResult>,
        query: String,
        topK: Int,
        pageIndex: Map<String, List<Int>> = emptyMap(),
    ): String {
        val entries = results.map { r ->
            buildJsonObject {
                put("source", JsonPrimitive("codex"))
                put("chunkId", JsonPrimitive(r.chunkId))
                put("chunkText", JsonPrimitive(r.chunkText.take(500)))
                put("sectionPath", JsonPrimitive(r.sectionPath))
                put("headingLevel", JsonPrimitive(r.headingLevel))
                put("sourceDocument", JsonPrimitive(r.sourceDocument))
                put("similarity", JsonPrimitive(r.similarity))
                put("confidence", JsonPrimitive(r.confidence))
                put("doubtful", JsonPrimitive(r.doubtful))
                // CDX-PAGE-PROVENANCE-3 : additive page localisation, omitted
                // when the section does not resolve (backward compat).
                pageIndex[r.sectionPath]?.let { pages ->
                    put("pages", JsonArray(pages.map { JsonPrimitive(it) }))
                }
            }
        }
        val composite = buildJsonObject {
            put("source", JsonPrimitive("brooklyn"))
            put("query", JsonPrimitive(query))
            put("topK", JsonPrimitive(topK))
            put("entries", JsonArray(entries))
            put("count", JsonPrimitive(entries.size))
        }
        return Json { prettyPrint = true }.encodeToString(composite)
    }

    /**
     * Serialization seam for the typed vibecoding context
     * (`composite-context-vibecoding.json`) — mirrors [buildCompositeJson].
     */
    internal fun buildVibecodingJson(docsSection: String, query: String, topK: Int, count: Int): String {
        val budget = buildJsonObject {
            put("totalTokenBudget", JsonPrimitive(8000))
            put("eager", JsonPrimitive(0.40))
            put("rag", JsonPrimitive(0.30))
            put("graphify", JsonPrimitive(0.20))
            put("docs", JsonPrimitive(0.10))
        }
        val vibecodingJson = buildJsonObject {
            put("source", JsonPrimitive("brooklyn"))
            put("query", JsonPrimitive(query))
            put("topK", JsonPrimitive(topK))
            put("docsSection", JsonPrimitive(docsSection))
            put("budget", budget)
            put("count", JsonPrimitive(count))
        }
        return Json { prettyPrint = true }.encodeToString(vibecodingJson)
    }

    @TaskAction
    fun execute() {
        val q = query.orNull ?: "architecture du workspace"
        val k = topK.orNull?.toIntOrNull() ?: 10

        val store = storeOverride.orNull ?: RagVectorStore(
            host = pgHost.get(),
            port = pgPort.get().toInt(),
            database = pgDatabase.get(),
            username = pgUser.get(),
            password = pgPassword.get()
        )
        val results: List<RetrieveResult> = kotlinx.coroutines.runBlocking {
            searchDoubtAware(store, q, k)
        }

        // ── JSON compatible N3/N4 — champs de doute additifs (backward compat) ──
        val entries = results

        // ── EPIC 3 : typed ContextChannel.Docs + CompositeContext ──
        // CDX-DOUBT-BRIDGE-2 : the Docs channel honors the socle doubt policy.
        val docsContent = buildDocsContent(results, excludeDoubtfulDocs.getOrElse(false))
        val docsChannel = ContextChannel.Docs(docsContent)

        val config = CompositeContextConfig(
            totalTokenBudget = 8000,
            budgetEagerLazy = 0.40,
            budgetRag = 0.30,
            budgetGraphify = 0.20,
            budgetDocs = 0.10,
            budgetOverhead = 0.0
        )
        val budget = ChannelBudget.fromConfig(config)

        val typedCompositeContext = CompositeContext(
            eagerSection = "",
            ragSection = "",
            graphifySection = buildGraphifySection(),
            docsSection = budget.applyBudget(listOf(docsChannel)).first().content,
            config = config
        )

        // ── Écriture JSON compatible (N3/N4 existant) ──
        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        output.writeText(buildCompositeJson(results, q, k, buildPageIndex()))

        // ── Écriture vibecoding typed context (EPIC 3) ──
        val typedFile = java.io.File(output.parentFile, "composite-context-vibecoding.json")
        typedFile.writeText(
            buildVibecodingJson(
                docsSection = typedCompositeContext.docsSection,
                query = q,
                topK = k,
                count = entries.size,
            )
        )

        Metadata.writeTo(
            output.parentFile,
            Metadata.forBrooklyn(type = "composite-context", sessions = entries.size)
        )

        logger.lifecycle(
            "[codex] generateCompositeContext — {} entries, docsSection={} tokens → {}",
            entries.size,
            ContextChannel.estimateTokens(typedCompositeContext.docsSection),
            output.absolutePath
        )
    }
}
