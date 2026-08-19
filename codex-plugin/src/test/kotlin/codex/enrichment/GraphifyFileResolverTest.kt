package codex.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * TDD — EPIC CDX-4-1 : `GraphifyFileResolver` adapter (lit `graph.json`).
 *
 * Le resolver lit un fichier `graph.json` au format `GraphModel` produit par
 * graphify-gradle (N0) et résout les nœuds par label de section. Aucune
 * dépendance compile vers graphify-gradle — le contrat est le fichier JSON
 * (contrat N0, pas de couplage Gradle).
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class GraphifyFileResolverTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `resolve returns empty for empty graph json`() {
        val file = tmp.resolve("empty.json").toFile()
        file.writeText("""{"nodes":[],"edges":[],"communities":[]}""")
        val resolver = GraphifyFileResolver(file)
        assertTrue(resolver.resolve("Anything").isEmpty())
    }

    @Test
    fun `resolve returns node ids matching label exactly`() {
        val file = tmp.resolve("graph.json").toFile()
        file.writeText(
            """
            {"nodes":[
              {"id":"n1","label":"Architecture","type":"concept"},
              {"id":"n2","label":"Testing","type":"concept"}
            ],"edges":[],"communities":[]}
            """.trimIndent()
        )
        val resolver = GraphifyFileResolver(file)
        assertEquals(listOf("n1"), resolver.resolve("Architecture"))
    }

    @Test
    fun `resolve is case insensitive on label match`() {
        val file = tmp.resolve("graph.json").toFile()
        file.writeText(
            """
            {"nodes":[
              {"id":"n1","label":"Architecture","type":"concept"}
            ],"edges":[],"communities":[]}
            """.trimIndent()
        )
        val resolver = GraphifyFileResolver(file)
        assertEquals(listOf("n1"), resolver.resolve("architecture"))
    }

    @Test
    fun `resolve returns empty when no label matches`() {
        val file = tmp.resolve("graph.json").toFile()
        file.writeText(
            """
            {"nodes":[
              {"id":"n1","label":"Architecture","type":"concept"}
            ],"edges":[],"communities":[]}
            """.trimIndent()
        )
        val resolver = GraphifyFileResolver(file)
        assertTrue(resolver.resolve("Nonexistent").isEmpty())
    }

    @Test
    fun `resolve handles missing nodes field gracefully`() {
        val file = tmp.resolve("partial.json").toFile()
        file.writeText("""{"edges":[]}""")
        val resolver = GraphifyFileResolver(file)
        assertTrue(resolver.resolve("Anything").isEmpty())
    }

    @Test
    fun `resolve returns multiple node ids when several labels match`() {
        val file = tmp.resolve("graph.json").toFile()
        file.writeText(
            """
            {"nodes":[
              {"id":"n1","label":"Architecture","type":"concept"},
              {"id":"n2","label":"architecture","type":"module"},
              {"id":"n3","label":"Testing","type":"concept"}
            ],"edges":[],"communities":[]}
            """.trimIndent()
        )
        val resolver = GraphifyFileResolver(file)
        assertEquals(listOf("n1", "n2"), resolver.resolve("Architecture"))
    }
}