package codex.tasks

import education.cccp.contracts.context.ChannelBudget
import education.cccp.contracts.context.CompositeContext
import education.cccp.contracts.context.CompositeContextConfig
import education.cccp.contracts.context.ContextChannel
import codex.Metadata
import codex.store.CodexVectorStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CodexCompositeContextTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val query: Property<String>

    @get:Input
    @get:Optional
    abstract val topK: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val q = query.orNull ?: "architecture du workspace"
        val k = topK.orNull?.toIntOrNull() ?: 10

        val store = CodexVectorStore()
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
            graphifySection = "",
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
