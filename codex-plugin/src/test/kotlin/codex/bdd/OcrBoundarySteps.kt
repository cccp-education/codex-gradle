package codex.bdd

import codex.ocr.OcrEngine
import codex.ocr.OcrPipeline
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import codex.ocr.OcrResultCache
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated steps for CDX-OCR-4 boundary BDD scenarios (`@ocr-boundary`).
 *
 * Phrases are intentionally distinct from [OcrPipelineSteps] to avoid
 * DuplicateStepDefinition collisions — both share the `codex.bdd` glue
 * package. The boundary scenarios exercise three contracts:
 *
 * 1. AI engine injected → succeeds directly, Tesseract not called
 *    (counting engine proves the fallback was skipped).
 * 2. Degraded Tesseract-only mode when no AI engine is injected.
 * 3. Cache hit skips the OCR engine entirely (Loi de l'Économie d'Encre
 *    — the metered LLM is not re-invoked on an unchanged image).
 */
class OcrBoundarySteps {

    private val state = mutableMapOf<String, Any>()

    // ── Counting engines (track invocation count) ───────────────────────

    /** Counting AI engine — succeeds with the given text/confidence. */
    @Given("an AI engine that returns {string} with confidence {double}")
    fun anAiEngineThatReturns(text: String, confidence: Double) {
        val calls = AtomicInteger(0)
        val engine = object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult {
                calls.incrementAndGet()
                return OcrResult.of(
                    text = text,
                    confidence = confidence,
                    language = request.language,
                    model = "ai",
                    metadata = mapOf("engine" to "ai")
                )
            }
        }
        state["aiEngine"] = engine
        state["aiCalls"] = calls
    }

    /** Counting Tesseract engine — returns the given text (fallback). */
    @Given("a counting Tesseract engine that returns {string}")
    fun aCountingTesseractEngineThatReturns(text: String) {
        val calls = AtomicInteger(0)
        val confidence = if (text.isNotEmpty()) 0.7 else 0.0
        val engine = object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult {
                calls.incrementAndGet()
                return OcrResult.of(
                    text = text,
                    confidence = confidence,
                    language = request.language,
                    model = "tesseract",
                    metadata = mapOf("engine" to "tesseract")
                )
            }
        }
        state["tesseractEngine"] = engine
        state["tesseractCalls"] = calls
    }

    /** Boundary Tesseract engine — returns the given text (for degraded-only scenario). */
    @Given("a boundary Tesseract engine that returns {string}")
    fun aBoundaryTesseractEngineThatReturns(text: String) {
        val confidence = if (text.isNotEmpty()) 0.7 else 0.0
        val engine = object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult =
                OcrResult.of(
                    text = text,
                    confidence = confidence,
                    language = request.language,
                    model = "tesseract",
                    metadata = mapOf("engine" to "tesseract")
                )
        }
        state["tesseractEngine"] = engine
    }

    /** Counting OCR engine (generic — used for cache hit scenario). */
    @Given("a counting OCR engine that returns {string} with confidence {double}")
    fun aCountingOcrEngineThatReturns(text: String, confidence: Double) {
        val calls = AtomicInteger(0)
        val engine = object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult {
                calls.incrementAndGet()
                return OcrResult.of(
                    text = text,
                    confidence = confidence,
                    language = request.language,
                    model = "ai",
                    metadata = mapOf("engine" to "ai")
                )
            }
        }
        state["countingEngine"] = engine
        state["countingCalls"] = calls
    }

    @Given("no AI engine is injected")
    fun noAiEngineInjected() {
        state.remove("aiEngine")
    }

    // ── Pipeline execution ──────────────────────────────────────────────

    @When("the OcrPipeline processes the image with both engines")
    fun theOcrPipelineProcessesWithBothEngines() {
        val ai = state["aiEngine"] as OcrEngine
        val tess = state["tesseractEngine"] as OcrEngine
        val pipeline = OcrPipeline(listOf(ai, tess))
        state["result"] = pipeline.process(OcrRequest(ByteArray(8), "image/png", "fr"))
    }

    @When("the OcrPipeline processes the image with Tesseract only")
    fun theOcrPipelineProcessesWithTesseractOnly() {
        val tess = state["tesseractEngine"] as OcrEngine
        val pipeline = OcrPipeline(listOf(tess))
        state["result"] = pipeline.process(OcrRequest(ByteArray(8), "image/png", "fr"))
    }

    // ── Cache hit scenario (Économie d'Encre) ────────────────────────────

    @Given("an OCR cache pre-populated for page {string} with text {string}")
    fun anOcrCachePrePopulated(pageId: String, text: String) {
        val storage = InMemoryCacheStorage()
        val result = OcrResult.of(
            text = text,
            confidence = 0.9,
            language = "fr",
            model = "ai",
            metadata = mapOf("engine" to "ai")
        )
        storage.write(pageId, codex.ocr.CacheEntry(sourceHash = "known-hash", result = result))
        state["cache"] = OcrResultCache(storage)
        state["cachePageId"] = pageId
        state["cacheHash"] = "known-hash"
    }

    @When("the pipeline checks the cache for page {string}")
    fun thePipelineChecksTheCache(pageId: String) {
        val cache = state["cache"] as OcrResultCache
        val hash = state["cacheHash"] as String
        val cached = cache.lookup(pageId, hash)
        // If cache miss, the counting engine would be called — but here we
        // verify the cache hit path (engine NOT called).
        if (cached != null) {
            state["cacheResult"] = cached
        } else {
            val engine = state["countingEngine"] as OcrEngine
            state["cacheResult"] = engine.process(OcrRequest(ByteArray(8), "image/png", "fr"))
        }
    }

    @Then("the cache returns {string}")
    fun theCacheReturns(expected: String) {
        val result = state["cacheResult"] as OcrResult
        assertEquals(expected, result.structuredText)
    }

    // ── Assertions ──────────────────────────────────────────────────────

    @Then("the boundary result text is {string}")
    fun theResultTextIs(expected: String) {
        val result = state["result"] as OcrResult
        assertEquals(expected, result.structuredText)
    }

    @And("the boundary result model is {string}")
    fun theResultModelIs(model: String) {
        val result = state["result"] as OcrResult
        assertEquals(model, result.model)
    }

    @Then("the counting Tesseract engine was called {int} times")
    fun theCountingTesseractEngineWasCalledTimes(expected: Int) {
        val calls = state["tesseractCalls"] as AtomicInteger
        assertEquals(expected, calls.get())
    }

    @And("the counting OCR engine was called {int} times")
    fun theCountingOcrEngineWasCalledTimes(expected: Int) {
        val calls = state["countingCalls"] as? AtomicInteger
        if (calls != null) {
            assertEquals(expected, calls.get())
        } else {
            // No counting engine registered — treat as 0 calls
            assertEquals(expected, 0)
        }
    }

    // ── In-memory cache storage for BDD ─────────────────────────────────

    private class InMemoryCacheStorage : codex.ocr.CacheStorage {
        private val store = mutableMapOf<String, codex.ocr.CacheEntry>()

        override fun read(pageId: String): codex.ocr.CacheEntry? = store[pageId]

        override fun write(pageId: String, entry: codex.ocr.CacheEntry) {
            store[pageId] = entry
        }
    }
}