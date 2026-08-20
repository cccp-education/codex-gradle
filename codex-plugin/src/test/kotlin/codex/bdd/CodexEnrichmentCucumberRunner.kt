package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_enrich_json_ldd.feature` (CDX-4-4).
 *
 * Scoped via `@SelectClasspathResource` so only the enrichment feature runs
 * — pattern S-082 (`CodexOntologyCucumberRunner`, `CodexLicenceCucumberRunner`).
 * Glue is bound to `codex.bdd` so [EnrichmentSteps] are discovered without
 * pulling unrelated glue from [PipelineSteps] or [OcrPipelineSteps].
 *
 * Pure BDD: the domain `codex.enrichment` was created in S-080, the task
 * `EnrichJsonLddTask` wired in S-086, and the Graphify channel wired in
 * S-087. This runner drives the four enrichment scenarios end-to-end via
 * Gradle's `ProjectBuilder` — no production code is modified.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_enrich_json_ldd.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-enrichment.html, json:build/reports/cucumber-enrichment.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@enrichment and not @wip and not @integration"
)
class CodexEnrichmentCucumberRunner