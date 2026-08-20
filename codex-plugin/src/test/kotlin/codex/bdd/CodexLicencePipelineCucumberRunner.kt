package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_licence_routing.feature` (CDX-6-3).
 *
 * Scoped via `@SelectClasspathResource` so only the licence-routing feature
 * runs — pattern S-082. Glue is bound to `codex.bdd` so
 * [LicencePipelineRoutingSteps] are discovered.
 *
 * Pure BDD: the routing was implemented in CDX-6-1 (task properties) and
 * CDX-6-2 (extension + plugin wiring). This runner validates the behaviour
 * end-to-end via Gradle's `ProjectBuilder` — no production code is modified.
 *
 * Note: `@licence @routing` tags are shared with `codex_export_routed.feature`
 * (CDX-5-3). To avoid tag collision, this runner is scoped to the specific
 * feature file via `@SelectClasspathResource`, and the steps class
 * [LicencePipelineRoutingSteps] uses distinct step phrases ("codex pipeline",
 * "transform corpus to pdf") that do not overlap with [LicenceRoutingSteps].
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_licence_routing.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-licence-pipeline-routing.html, json:build/reports/cucumber-licence-pipeline-routing.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@licence and @routing and not @wip and not @integration"
)
class CodexLicencePipelineCucumberRunner