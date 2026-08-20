package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_pipeline_unified_chunking.feature`
 * (CDX-UNIFY-2) — pattern S-082.
 *
 * Scoped via `@SelectClasspathResource` so only the unified-chunking
 * feature runs. Glue is bound to `codex.bdd` so
 * [UnifiedPipelineChunkingSteps] are discovered.
 *
 * Pure BDD: the unification was implemented in CDX-UNIFY-1 (refactor
 * delegation `chunkMd` → `SemanticChunker.chunk()`). This runner validates
 * the pipeline output contract end-to-end via Gradle's `ProjectBuilder` —
 * no production code is modified.
 *
 * Step phrases are prefixed with "unified" to avoid DuplicateStepDefinition
 * collisions with [LicencePipelineRoutingSteps] (CDX-6-3) which drives the
 * same task with different phrasing.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_pipeline_unified_chunking.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-pipeline-unified.html, json:build/reports/cucumber-pipeline-unified.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@pipeline and @unify and not @wip and not @integration"
)
class CodexPipelineUnifyCucumberRunner