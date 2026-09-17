package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_page_provenance.feature`
 * (CDX-PAGE-PROVENANCE-4) — pattern S-082 (10th dedicated runner codex).
 *
 * Scoped via `@SelectClasspathResource` so only the page provenance feature
 * runs, filtered to `@provenance`. Glue is bound to `codex.bdd` so
 * [PageProvenanceSteps] are discovered without pulling unrelated glue.
 *
 * The scenarios lock the derived join (TOC × chunks × acquisition report):
 * a chunk localises to the page of its section, multi-page sections carry
 * every page, OCR-polluted titles are canonicalized, a polluted leaf is
 * resolved by the ancestor walk, unresolved sections degrade silently, the
 * acquisition doubt attaches to the doubtful page, and the N3 composite entry
 * exposes the pages additively.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_page_provenance.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-page-provenance.html, json:build/reports/cucumber-page-provenance.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@provenance and not @wip and not @integration"
)
class CodexPageProvenanceCucumberRunner
