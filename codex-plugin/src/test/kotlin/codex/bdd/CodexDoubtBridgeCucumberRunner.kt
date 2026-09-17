package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_doubt_bridge.feature` (CDX-DOUBT-BRIDGE)
 * — pattern S-082 (9th dedicated runner codex).
 *
 * Scoped via `@SelectClasspathResource` so only the doubt bridge feature runs,
 * filtered to `@doubt-bridge`. Glue is bound to `codex.bdd` so [DoubtBridgeSteps]
 * are discovered without pulling unrelated glue.
 *
 * The scenarios lock the full bridge: an `[ILLISIBLE]` chunk is ingested
 * flagged doubtful with zero confidence (socle policy), a clean chunk keeps
 * full confidence, the Docs channel annotates doubtful chunks by default and
 * excludes them in opt-in, and the composite-context retrieval uses the
 * doubt-aware socle search.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_doubt_bridge.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-doubt-bridge.html, json:build/reports/cucumber-doubt-bridge.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@doubt-bridge and not @wip and not @integration"
)
class CodexDoubtBridgeCucumberRunner
