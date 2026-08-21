package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_session_memory.feature` (CDX-RC-04-4).
 *
 * Scoped via `@SelectClasspathResource` so only the session-memory feature
 * runs — pattern S-082 (6th runner codex). Glue is bound to `codex.bdd` so
 * [SessionMemorySteps] are discovered without pulling unrelated glue.
 *
 * Pure BDD: uses an in-memory [FakeSessionMemory] (implements
 * [contracts.runtime.SessionMemoryContract]) to validate the save/load
 * contract, plus pure-domain assertions on [codex.profile.ProfileEmbedding]
 * and [codex.profile.ProfileStatements]. No real pgvector, no Docker.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_session_memory.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-session-memory.html, json:build/reports/cucumber-session-memory.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@session-memory and not @wip and not @integration"
)
class CodexSessionMemoryCucumberRunner