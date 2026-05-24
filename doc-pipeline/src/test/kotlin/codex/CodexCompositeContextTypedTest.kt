package codex

import cccp.vibecoding.contracts.context.ChannelBudget
import cccp.vibecoding.contracts.context.CompositeContext
import cccp.vibecoding.contracts.context.CompositeContextConfig
import cccp.vibecoding.contracts.context.ContextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC 3 propagation Multi-Canal Convergent N1→N2.
 * Vérifie que codex-gradle produit des objets [ContextChannel.Docs] typés
 * et un [CompositeContext] conforme au contrat vibecoding.
 */
class CodexCompositeContextTypedTest {

    private val config = CompositeContextConfig(
        totalTokenBudget = 8000,
        budgetEagerLazy = 0.40,
        budgetRag = 0.30,
        budgetGraphify = 0.20,
        budgetDocs = 0.10,
        budgetOverhead = 0.0
    )

    @Test
    fun `empty codex results produce empty CompositeContext with Docs channel`() {
        val ctx = CompositeContext(
            eagerSection = "",
            ragSection = "",
            graphifySection = "",
            docsSection = "",
            config = config
        )
        val channels = ctx.toChannels()

        assertEquals(5, channels.size)
        val docsChannel = channels.find { it is ContextChannel.Docs }
        assertNotNull(docsChannel)
        assertEquals("CONTEXTE_DOCS", docsChannel!!.sectionHeader)
        assertEquals(0.10, docsChannel.budgetProportion)
    }

    @Test
    fun `codex results populate docsSection in CompositeContext`() {
        val docsContent = """
            [Document: AFNOR_Referentiel.adoc]
            Chapitre 2: Competences professionnelles
            Sous-section: Evaluation des acquis
            
            [Document: REAC_GuidePedagogique.adoc]
            Module 3: Methodes pedagogiques
            Approche par competences recommandee pour les formations FPA
        """.trimIndent()

        val ctx = CompositeContext(
            eagerSection = "EAGER context",
            ragSection = "RAG context",
            graphifySection = "Graphify context",
            docsSection = docsContent,
            config = config
        )

        assertEquals(docsContent, ctx.docsSection)
        val channels = ctx.toChannels()
        val docsChannel = channels.find { it is ContextChannel.Docs }!!
        assertTrue(docsChannel.contentNonEmpty)
        assertTrue(docsChannel.content.contains("AFNOR_Referentiel"))
        assertTrue(docsChannel.content.contains("Competences professionnelles"))
    }

    @Test
    fun `ChannelBudget allocates 10% to Docs`() {
        val budget = ChannelBudget(
            totalTokenBudget = 10_000,
            budgetEager = 0.40,
            budgetRag = 0.30,
            budgetGraphify = 0.20,
            budgetDocs = 0.10,
            budgetResource = 0.0
        )
        assertEquals(1000, budget.docsTokens)
        assertEquals(4000, budget.eagerTokens)
        assertEquals(3000, budget.ragTokens)
        assertEquals(2000, budget.graphifyTokens)
        assertEquals(0, budget.resourceTokens)
    }

    @Test
    fun `applyBudget truncates Docs channel to 10%`() {
        val longDocs = "doc ".repeat(5000)  // ~10k chars
        val channels = listOf(
            ContextChannel.Eager("eager"),
            ContextChannel.Rag("rag"),
            ContextChannel.Graphify("graph"),
            ContextChannel.Docs(longDocs),
            ContextChannel.Resource("res")
        )
        val budget = ChannelBudget(totalTokenBudget = 8000)
        val truncated = budget.applyBudget(channels)

        val docsChannel = truncated.find { it is ContextChannel.Docs }!!
        // docsTokens = 8000 * 0.10 = 800 tokens => ~2800 chars
        val originalTokens = ContextChannel.estimateTokens(longDocs)
        val truncatedTokens = ContextChannel.estimateTokens(docsChannel.content)
        assertTrue(truncatedTokens <= 800 + 10, "Docs truncated to budget: $truncatedTokens <= 800")
        assertTrue(truncatedTokens < originalTokens, "Truncation reduced content size")
    }

    @Test
    fun `fromConfig bridges CompositeContextConfig to ChannelBudget`() {
        val budget = ChannelBudget.fromConfig(config)

        assertEquals(8000, budget.totalTokenBudget)
        assertEquals(0.40, budget.budgetEager)
        assertEquals(0.30, budget.budgetRag)
        assertEquals(0.20, budget.budgetGraphify)
        assertEquals(0.10, budget.budgetDocs)
        assertEquals(0.0, budget.budgetResource)
    }

    @Test
    fun `channelsWithBudget returns truncated channels from CompositeContext`() {
        val ctx = CompositeContext(
            eagerSection = "eager ".repeat(5000),
            ragSection = "rag ".repeat(5000),
            graphifySection = "graph ".repeat(5000),
            docsSection = "docs ".repeat(5000),
            config = config
        )
        val budget = ChannelBudget.fromConfig(config)
        val channels = ctx.channelsWithBudget(budget)

        assertEquals(5, channels.size)
        val docsChannel = channels.find { it is ContextChannel.Docs }!!
        val docsTokens = ContextChannel.estimateTokens(docsChannel.content)
        // 8000 total * 0.10 = 800 tokens budget for Docs
        assertTrue(docsTokens <= 800 + 10, "Docs truncated to ~800 tokens budget")
    }

    @Test
    fun `ContextChannel Docs has correct metadata`() {
        val docs = ContextChannel.Docs("corpus content")

        assertEquals("Docs", docs.source)
        assertEquals(0.10, docs.budgetProportion)
        assertEquals("Codex/Docs", docs.name)
        assertEquals("Corpus documentaire (codex pgvector)", docs.description)
        assertEquals("CONTEXTE_DOCS", docs.sectionHeader)
    }
}
