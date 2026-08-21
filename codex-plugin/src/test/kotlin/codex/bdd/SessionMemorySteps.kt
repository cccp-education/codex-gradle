package codex.bdd

import codex.profile.ProfileEmbedding
import codex.profile.ProfileStatements
import contracts.runtime.LearnerProfile
import contracts.runtime.SessionMemoryContract
import io.cucumber.java8.En
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Cucumber steps for `codex_session_memory.feature` (CDX-RC-04-4).
 *
 * Pure BDD — no real pgvector, no Docker. Uses an in-memory
 * [FakeSessionMemory] implementing [SessionMemoryContract] to validate the
 * save/load contract, plus pure-domain assertions on [ProfileEmbedding] and
 * [ProfileStatements] (object purs, unit-testable).
 *
 * Step prefixes are "session memory" / "profile" to avoid DuplicateStepDefinition
 * with other codex BDD suites (bug S-088).
 */
class SessionMemorySteps : En {

    private val store: MutableMap<String, LearnerProfile> = mutableMapOf()
    private val memory: SessionMemoryContract = FakeSessionMemory(store)
    private var currentProfile: LearnerProfile? = null
    private var loadedProfile: LearnerProfile? = null
    private var embeddingText: String = ""

    init {

        Given("a session memory bridge with an in-memory fake store") {
            store.clear()
            currentProfile = null
            loadedProfile = null
            embeddingText = ""
        }

        // ── Embedding scenarios ──────────────────────────────────────────

        Given("a learner profile with no weak points and no annotations") {
            currentProfile = LearnerProfile("learner-empty", "formation-empty")
        }

        Given("a learner profile with weak points {string} and {string}") { wp1: String, wp2: String ->
            currentProfile = LearnerProfile("learner-wp", "formation-wp", weakPoints = listOf(wp1, wp2))
        }

        Given("a learner profile with annotations {string} to {string}") { key: String, value: String ->
            currentProfile = LearnerProfile("learner-ann", "formation-ann", annotations = mapOf(key to value))
        }

        Given("a learner profile with completed modules {string} and {string}") { m1: String, m2: String ->
            currentProfile = LearnerProfile("learner-mod", "formation-mod", completedModules = listOf(m1, m2))
        }

        When("the profile embedding text is computed") {
            embeddingText = ProfileEmbedding.textToEmbed(currentProfile!!)
        }

        Then("the embedding text is empty") {
            assertEquals("", embeddingText)
        }

        Then("the embedding text contains {string}") { fragment: String ->
            assertTrue(embeddingText.contains(fragment), "embedding text should contain '$fragment', was: $embeddingText")
        }

        // ── Save/load scenarios ──────────────────────────────────────────

        Given("a learner profile for learner {string} in formation {string} with weak points {string}") {
            learnerId: String, formationId: String, weakPoints: String ->
            currentProfile = LearnerProfile(
                learnerId, formationId,
                weakPoints = weakPoints.split(", ").map { it.trim() }
            )
        }

        When("the profile is saved via the session memory bridge") {
            memory.save(currentProfile!!)
        }

        When("a learner profile for learner {string} in formation {string} with weak points {string} is saved") {
            learnerId: String, formationId: String, weakPoints: String ->
            val p = LearnerProfile(learnerId, formationId, weakPoints = listOf(weakPoints))
            memory.save(p)
        }

        When("the profile is loaded for learner {string} and formation {string}") {
            learnerId: String, formationId: String ->
            loadedProfile = memory.load(learnerId, formationId)
        }

        Then("the loaded profile has weak points {string}") { weakPoints: String ->
            assertNotNull(loadedProfile, "profile should be loaded")
            val expected = weakPoints.split(", ").map { it.trim() }
            assertEquals(expected, loadedProfile!!.weakPoints)
        }

        Then("loading for learner {string} and formation {string} returns weak points {string}") {
            learnerId: String, formationId: String, weakPoints: String ->
            val loaded = memory.load(learnerId, formationId)
            assertNotNull(loaded)
            val expected = weakPoints.split(", ").map { it.trim() }
            assertEquals(expected, loaded!!.weakPoints)
        }

        Then("no profile is returned") {
            assertNull(loadedProfile)
        }

        // ── SQL template scenarios ───────────────────────────────────────

        When("the upsert SQL template is inspected") {
            // No-op — assertions inspect ProfileStatements directly
        }

        Then("the template inserts into {string}") { table: String ->
            val sql = ProfileStatements.upsertProfile()
            assertTrue(sql.contains("INSERT INTO $table"), "upsert should insert into $table: $sql")
        }

        Then("the template uses {string}") { clause: String ->
            val sql = ProfileStatements.upsertProfile()
            assertTrue(sql.contains(clause), "upsert should contain '$clause': $sql")
        }

        When("the schema DDL is inspected") {
            // No-op — assertions inspect ProfileStatements directly
        }

        Then("the DDL creates table {string}") { table: String ->
            val ddl = ProfileStatements.initSchema().joinToString(" ")
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS $table"), "DDL should create $table: $ddl")
        }

        Then("the DDL defines a composite primary key on {string} and {string}") { col1: String, col2: String ->
            val ddl = ProfileStatements.initSchema().joinToString(" ")
            assertTrue(ddl.contains("PRIMARY KEY ($col1, $col2)"), "composite PK: $ddl")
        }

        Then("the DDL defines an embedding column of dimension {int}") { dim: Int ->
            val ddl = ProfileStatements.initSchema().joinToString(" ")
            assertTrue(ddl.contains("embedding vector($dim)"), "embedding $dim-dim: $ddl")
        }
    }

    private fun key(learnerId: String, formationId: String) = "$learnerId|$formationId"
}

/**
 * In-memory fake implementing [SessionMemoryContract] — pure BDD, no DB.
 */
private class FakeSessionMemory(private val store: MutableMap<String, LearnerProfile>) : SessionMemoryContract {
    override fun save(profile: LearnerProfile) {
        store["${profile.learnerId}|${profile.formationId}"] = profile
    }

    override fun load(learnerId: String, formationId: String): LearnerProfile? {
        return store["$learnerId|$formationId"]
    }
}