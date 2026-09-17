package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `codex_ocr_quality.feature` (OCR-QUALITY-1)
 * — pattern S-082 (8th runner codex).
 *
 * Scoped via `@SelectClasspathResource` so only the OCR quality feature runs,
 * filtered to `@ocr-quality`. Glue is bound to `codex.bdd` so [OcrQualitySteps]
 * are discovered without pulling unrelated glue.
 *
 * The scenarios lock the real-confidence contract: Tesseract TSV word scores
 * averaged into a normalized confidence (not the historical 0.7 placeholder),
 * garbage credited with no confidence, and a degraded engine whose text is
 * preserved by the pipeline while the doubt is honestly flagged.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/codex_ocr_quality.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-ocr-quality.html, json:build/reports/cucumber-ocr-quality.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@ocr-quality and not @wip and not @integration"
)
class CodexOcrQualityCucumberRunner
