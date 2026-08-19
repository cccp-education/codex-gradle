package codex.ontology

import codex.tasks.LddNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-3-1 : Domaine `codex.ontology` + `OntologyDeriver` object pur.
 *
 * `OntologyDeriver.derive(lddNodes, sqlScript)` analyse le SQL LMD généré
 * par `ImportBookSqlTask` pour *calculer* (pas deviner) la cartographie
 * ontologique du corpus livresque :
 *
 *  - Bounded contexts  : groupements de tables liées par FK (connectivité).
 *  - Aggregates        : tables racines référencées via ON DELETE CASCADE
 *                        + leurs entités dépendantes.
 *  - Value objects     : tables sans FK sortantes (pas de REFERENCES dans
 *                        leur CREATE TABLE).
 *
 * 3 requêtes archétypales (P1-19 spec) — baby-step TDD strict
 * RED (types inexistants) → GREEN → REFACTOR.
 */
class OntologyDeriverTest {

    private val bookSql = """
        -- codex: collectBookSql
        CREATE TABLE IF NOT EXISTS book (
            id          SERIAL PRIMARY KEY,
            title       TEXT NOT NULL,
            created_at  TIMESTAMP DEFAULT now()
        );

        CREATE TABLE IF NOT EXISTS documents (
            id          SERIAL PRIMARY KEY,
            book_id     INTEGER REFERENCES book(id) ON DELETE CASCADE,
            title       TEXT NOT NULL,
            level       INTEGER NOT NULL DEFAULT 0,
            parent_id   INTEGER REFERENCES documents(id) ON DELETE CASCADE,
            sort_order  INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS paragraphs (
            id          SERIAL PRIMARY KEY,
            doc_id      INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            text        TEXT NOT NULL,
            position    INTEGER NOT NULL DEFAULT 0
        );
    """.trimIndent()

    @Test
    fun `derive with empty sql and empty nodes returns empty mapping`() {
        val mapping = OntologyDeriver.derive(emptyList(), "")
        assertTrue(mapping.boundedContexts.isEmpty())
        assertTrue(mapping.aggregates.isEmpty())
        assertTrue(mapping.valueObjects.isEmpty())
    }

    @Test
    fun `derive extracts table names from CREATE TABLE statements`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val allTables = mapping.boundedContexts.flatMap { it.tables }.toSet()
        assertTrue("book" in allTables, "book table should be extracted")
        assertTrue("documents" in allTables, "documents table should be extracted")
        assertTrue("paragraphs" in allTables, "paragraphs table should be extracted")
    }

    @Test
    fun `bounded contexts groups all FK-connected tables into one context`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        assertEquals(1, mapping.boundedContexts.size, "all 3 tables are FK-connected → 1 context")
        val ctx = mapping.boundedContexts.first()
        assertEquals(3, ctx.tables.size)
        assertTrue(ctx.tables.containsAll(setOf("book", "documents", "paragraphs")))
    }

    @Test
    fun `bounded contexts separates disconnected table groups`() {
        val disconnectedSql = """
            CREATE TABLE IF NOT EXISTS orders (
                id          SERIAL PRIMARY KEY,
                customer_id INTEGER REFERENCES customers(id)
            );
            CREATE TABLE IF NOT EXISTS customers (
                id          SERIAL PRIMARY KEY,
                name        TEXT
            );
            CREATE TABLE IF NOT EXISTS audit_log (
                id          SERIAL PRIMARY KEY,
                event       TEXT
            );
        """.trimIndent()
        val mapping = OntologyDeriver.derive(emptyList(), disconnectedSql)
        assertEquals(2, mapping.boundedContexts.size, "audit_log is disconnected → 2 contexts")
        val tablesByContext = mapping.boundedContexts.map { it.tables.toSet() }
        assertTrue(setOf("orders", "customers") in tablesByContext)
        assertTrue(setOf("audit_log") in tablesByContext)
    }

    @Test
    fun `aggregates identifies root tables referenced via ON DELETE CASCADE`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val roots = mapping.aggregates.map { it.rootTable }.toSet()
        assertTrue("book" in roots, "book is referenced by documents via CASCADE → root")
        assertTrue("documents" in roots, "documents is referenced by paragraphs via CASCADE → root")
    }

    @Test
    fun `aggregates collects dependent tables of each root`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val bookAggregate = mapping.aggregates.first { it.rootTable == "book" }
        assertTrue("documents" in bookAggregate.dependentTables,
            "documents depends on book via CASCADE")
        val documentsAggregate = mapping.aggregates.first { it.rootTable == "documents" }
        assertTrue("paragraphs" in documentsAggregate.dependentTables,
            "paragraphs depends on documents via CASCADE")
    }

    @Test
    fun `aggregates excludes FK without ON DELETE CASCADE`() {
        val noCascadeSql = """
            CREATE TABLE IF NOT EXISTS parent (
                id SERIAL PRIMARY KEY
            );
            CREATE TABLE IF NOT EXISTS child (
                id SERIAL PRIMARY KEY,
                parent_id INTEGER REFERENCES parent(id)
            );
        """.trimIndent()
        val mapping = OntologyDeriver.derive(emptyList(), noCascadeSql)
        assertTrue(mapping.aggregates.isEmpty(),
            "FK without CASCADE does not define an aggregate root")
    }

    @Test
    fun `value objects identifies tables without outgoing FK`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val voTables = mapping.valueObjects.map { it.table }.toSet()
        assertTrue("book" in voTables, "book has no outgoing REFERENCES → value object")
    }

    @Test
    fun `value objects excludes tables with outgoing REFERENCES`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val voTables = mapping.valueObjects.map { it.table }.toSet()
        assertTrue("documents" !in voTables, "documents has outgoing FK → not a value object")
        assertTrue("paragraphs" !in voTables, "paragraphs has outgoing FK → not a value object")
    }

    @Test
    fun `value objects collects columns from the CREATE TABLE body`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        val bookVo = mapping.valueObjects.first { it.table == "book" }
        assertTrue("id" in bookVo.columns)
        assertTrue("title" in bookVo.columns)
        assertTrue("created_at" in bookVo.columns)
    }

    @Test
    fun `OntologyMapping exposes all three views`() {
        val mapping = OntologyDeriver.derive(emptyList(), bookSql)
        assertEquals(1, mapping.boundedContexts.size)
        assertEquals(2, mapping.aggregates.size, "book + documents are CASCADE roots")
        assertEquals(1, mapping.valueObjects.size, "only book has no outgoing FK")
    }

    @Test
    fun `derive is pure - same input yields same output`() {
        val m1 = OntologyDeriver.derive(emptyList(), bookSql)
        val m2 = OntologyDeriver.derive(emptyList(), bookSql)
        assertEquals(m1, m2, "OntologyDeriver must be deterministic (pure object)")
    }

    @Test
    fun `derive uses lddNodes titles to name bounded contexts when available`() {
        val nodes = listOf(
            LddNode(title = "Applied AI for Enterprise Java", level = 1)
        )
        val mapping = OntologyDeriver.derive(nodes, bookSql)
        assertEquals(1, mapping.boundedContexts.size)
        val ctx = mapping.boundedContexts.first()
        assertTrue(ctx.name.isNotBlank(), "context name should derive from LDD or fallback")
    }
}