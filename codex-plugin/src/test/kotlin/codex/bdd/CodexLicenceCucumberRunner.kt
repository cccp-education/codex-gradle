package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_export_routed.feature` (CDX-5-3).
 *
 * Scoped via `@SelectClasspathResource` so only the licence-routing feature
 * runs — pattern S-082 (`CapsuleFormatCucumberRunner`). Glue is bound to
 * `codex.bdd` so [LicenceRoutingSteps] are discovered without pulling the
 * unrelated [PipelineSteps] glue.
 *
 * Pure BDD: the domain `codex.licence` was created in S-081 and the task
 * `DeployKnowledgeBaseRoutedTask` wired in S-082. This runner drives the
 * three routing scenarios end-to-end via Gradle's `ProjectBuilder` — no
 * production code is modified.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_export_routed.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-licence-routing.html, json:build/reports/cucumber-licence-routing.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@licence and @routing and not @wip and not @integration"
)
class CodexLicenceCucumberRunner