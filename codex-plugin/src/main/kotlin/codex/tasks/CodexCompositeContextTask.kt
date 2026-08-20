package codex.tasks

import contracts.context.ChannelBudget
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import contracts.context.ContextChannel
import codex.Metadata
import codex.enrichment.EnrichedLddNode
import codex.enrichment.GraphifySectionBuilder
import codex.store.CodexVectorStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
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

    // CDX-CR3-2 : propriétés pg exposées et wirees depuis CodexExtension.
    // Avant fix, la task instançait CodexVectorStore() avec localhost:5432
    // en dur — inutilisable hors localhost.
    // Les conventions garantissent le backward compat (localhost:5432/codex)
    // si la task est utilisée sans le plugin ou sans configurer l'extension.
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

    // CDX-4-3 : canal Graphify peuplé depuis le JSON enrichi produit par
    // `enrichJsonLdd` (List<EnrichedLddNode> sérialisée). Propriété
    // optionnelle — backward compat : absente → graphifySection = "".
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val enrichedJsonFile: RegularFileProperty

    /**
     * Builds the Graphify channel section text from the enriched LDD
     * JSON file. Returns an empty string when the file is absent, empty,
     * or invalid (degraded silent — backward compat with the previous
     * `graphifySection = ""` default).
     */
    internal fun buildGraphifySection(): String {
        val file = enrichedJsonFile.asFile.orNull ?: return ""
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

    @TaskAction
    fun execute() {
        val q = query.orNull ?: "architecture du workspace"
        val k = topK.orNull?.toIntOrNull() ?: 10

        val store = CodexVectorStore(
            host = pgHost.get(),
            port = pgPort.get().toInt(),
            database = pgDatabase.get(),
            username = pgUser.get(),
            password = pgPassword.get()
        )
        val results = store.searchBlocking(q, k)

        // ── JSON compatible N3/N4 (inchangé) ──
        val entries = results.map { r ->
            mapOf(
                "source" to "codex",
                "chunkId" to r.chunkId,
                "chunkText" to r.chunkText.take(500),
                "sectionPath" to r.sectionPath,
                "headingLevel" to r.headingLevel,
                "sourceDocument" to r.sourceDocument,
                "similarity" to r.similarity
            )
        }

        val composite = mapOf<String, Any>(
            "source" to "brooklyn",
            "query" to q,
            "topK" to k,
            "entries" to entries,
            "count" to entries.size
        )

        // ── EPIC 3 : typed ContextChannel.Docs + CompositeContext ──
        val docsContent = results.joinToString("\n\n") { r ->
            "[${r.sourceDocument} / ${r.sectionPath}] (similarity=${"%.3f".format(r.similarity)})\n${r.chunkText}"
        }
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
        output.writeText(Json { prettyPrint = true }.encodeToString(composite))

        // ── Écriture vibecoding typed context (EPIC 3) ──
        val typedFile = java.io.File(output.parentFile, "composite-context-vibecoding.json")
        val vibecodingJson = mapOf(
            "source" to "brooklyn",
            "query" to q,
            "topK" to k,
            "docsSection" to typedCompositeContext.docsSection,
            "budget" to mapOf(
                "totalTokenBudget" to config.totalTokenBudget,
                "eager" to config.budgetEagerLazy,
                "rag" to config.budgetRag,
                "graphify" to config.budgetGraphify,
                "docs" to config.budgetDocs
            ),
            "count" to entries.size
        )
        typedFile.writeText(Json { prettyPrint = true }.encodeToString(vibecodingJson))

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
