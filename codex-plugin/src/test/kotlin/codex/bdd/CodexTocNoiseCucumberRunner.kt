package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_toc_noise.feature` (C-4, S-233) —
 * pattern S-082 (11th dedicated runner codex).
 *
 * Scoped via `@SelectClasspathResource` so only the TOC-noise feature runs,
 * filtered to `@toc-noise`. Glue is bound to `codex.bdd` so [TocNoiseSteps]
 * are discovered without pulling unrelated glue.
 *
 * The scenarios lock the acquisition-local overlay: a dot-leader TOC heading
 * is ingested doubtful with zero confidence, a clean chunk keeps full
 * confidence, the illisible OCR policy is preserved, and a real title ending
 * with an ellipsis or dotted coordinates is never flagged.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_toc_noise.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-toc-noise.html, json:build/reports/cucumber-toc-noise.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@toc-noise and not @wip and not @integration"
)
class CodexTocNoiseCucumberRunner
