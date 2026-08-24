package codex.bdd

import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Dedicated Cucumber suite for `ocr-pipeline.feature` boundary scenarios
 * (CDX-OCR-4) — pattern S-082 (7th runner codex).
 *
 * Scoped via `@SelectClasspathResource` so only the OCR pipeline feature
 * runs, filtered to `@ocr-boundary` tags (the 3 new scenarios added in
 * CDX-OCR-4). Glue is bound to `codex.bdd` so [OcrBoundarySteps] are
 * discovered without pulling unrelated glue.
 *
 * Pure BDD: the boundary rule (AI-assisted OCR is actioned by the codebase
 * socle, not by codex) was implemented in CDX-OCR-1 (port injection),
 * CDX-OCR-2 (VisionOcrEngineAdapter on codebase side), and CDX-OCR-3
 * (purge of the codex AI stack). This runner validates three contracts
 * end-to-end via counting fake engines + in-memory cache:
 *
 * 1. Injected AI engine succeeds directly — Tesseract is not called
 *    (counting engine proves the fallback was skipped).
 * 2. Degraded Tesseract-only mode when no AI engine is injected.
 * 3. Cache hit skips the OCR engine entirely (Loi de l'Économie d'Encre
 *    — the metered LLM is not re-invoked on an unchanged image).
 *
 * Step phrases are prefixed differently from [OcrPipelineSteps] to avoid
 * DuplicateStepDefinition collisions (both share the `codex.bdd` glue
 * package, but the boundary steps use distinct Given/When/Then phrases).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/ocr-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codex.bdd")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-ocr-boundary.html, json:build/reports/cucumber-ocr-boundary.json"
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "@ocr-boundary and not @wip and not @integration"
)
class CodexOcrCucumberRunner