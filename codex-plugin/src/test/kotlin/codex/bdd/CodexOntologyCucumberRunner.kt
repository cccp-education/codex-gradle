package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_derive_ontology.feature` (CDX-3-3).
 *
 * Scoped via `@SelectClasspathResource` so only the ontology-derivation
 * feature runs — pattern S-082 (`CodexLicenceCucumberRunner`). Glue is
 * bound to `codex.bdd` so [OntologySteps] are discovered without pulling
 * the unrelated [PipelineSteps] or [LicenceRoutingSteps] glue.
 *
 * Pure BDD: the domain `codex.ontology` was created in S-079 and the
 * task `DeriveOntologyTask` wired in S-084. This runner drives the three
 * derivation scenarios end-to-end via Gradle's `ProjectBuilder` — no
 * production code is modified.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_derive_ontology.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-ontology-derivation.html, json:build/reports/cucumber-ontology-derivation.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@ontology and @derivation and not @wip and not @integration"
)
class CodexOntologyCucumberRunner