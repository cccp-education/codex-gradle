package codex.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Isolated unit tests for [TypographicAnalyzer] — pure typography analysis
 * extracted from `ExtractBookStructureTask` (CDX-7-1).
 *
 * `TypographicAnalyzer` computes dynamic header thresholds (h1/h2/h3/h4)
 * from font-size statistics (avg/max/min/range). It is a pure object with
 * no I/O, no Gradle, no PDF dependency — unit-testable in isolation.
 */
class TypographicAnalyzerTest {

    // ── computeHeaderThresholds — dynamic mode (range > thresholds) ────────────

    @Test
    fun `large range uses dynamic h1 = max - range * 0_05`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 24.0, min = 10.0, range = 14.0)
        assertEquals(24.0 - 14.0 * 0.05, t.h1, 0.0001)
    }

    @Test
    fun `large range uses dynamic h2 = max - range * 0_25`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 24.0, min = 10.0, range = 14.0)
        assertEquals(24.0 - 14.0 * 0.25, t.h2, 0.0001)
    }

    @Test
    fun `large range uses dynamic h3 = max - range * 0_50`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 24.0, min = 10.0, range = 14.0)
        assertEquals(24.0 - 14.0 * 0.50, t.h3, 0.0001)
    }

    @Test
    fun `large range uses dynamic h4 = max - range * 0_70`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 24.0, min = 10.0, range = 14.0)
        assertEquals(24.0 - 14.0 * 0.70, t.h4, 0.0001)
    }

    // ── computeHeaderThresholds — fallback mode (small range) ─────────────────

    @Test
    fun `range below 1_0 uses h1 = max * 0_95 fallback`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 20.0, min = 19.5, range = 0.5)
        assertEquals(20.0 * 0.95, t.h1, 0.0001)
        // h2 threshold falls through (range > 2.0 false → fallback)
        assertEquals(20.0 * 0.75, t.h2, 0.0001)
        assertEquals(20.0 * 0.50, t.h3, 0.0001)
        assertEquals(20.0 * 0.35, t.h4, 0.0001)
    }

    @Test
    fun `range between 1_0 and 2_0 uses h1 dynamic but h2 h3 h4 fallback`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 20.0, min = 18.0, range = 1.5)
        // h1 dynamic (range > 1.0)
        assertEquals(20.0 - 1.5 * 0.05, t.h1, 0.0001)
        // h2 fallback (range <= 2.0)
        assertEquals(20.0 * 0.75, t.h2, 0.0001)
        // h3 fallback (range <= 3.0)
        assertEquals(20.0 * 0.50, t.h3, 0.0001)
        // h4 fallback (range <= 4.0)
        assertEquals(20.0 * 0.35, t.h4, 0.0001)
    }

    @Test
    fun `range between 2_0 and 3_0 uses h1 h2 dynamic but h3 h4 fallback`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 20.0, min = 17.0, range = 2.5)
        assertEquals(20.0 - 2.5 * 0.05, t.h1, 0.0001)
        assertEquals(20.0 - 2.5 * 0.25, t.h2, 0.0001)
        assertEquals(20.0 * 0.50, t.h3, 0.0001)
        assertEquals(20.0 * 0.35, t.h4, 0.0001)
    }

    @Test
    fun `range between 3_0 and 4_0 uses h1 h2 h3 dynamic but h4 fallback`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 20.0, min = 16.0, range = 3.5)
        assertEquals(20.0 - 3.5 * 0.05, t.h1, 0.0001)
        assertEquals(20.0 - 3.5 * 0.25, t.h2, 0.0001)
        assertEquals(20.0 - 3.5 * 0.50, t.h3, 0.0001)
        assertEquals(20.0 * 0.35, t.h4, 0.0001)
    }

    @Test
    fun `zero range uses all fallbacks`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 12.0, min = 12.0, range = 0.0)
        assertEquals(12.0 * 0.95, t.h1, 0.0001)
        assertEquals(12.0 * 0.75, t.h2, 0.0001)
        assertEquals(12.0 * 0.50, t.h3, 0.0001)
        assertEquals(12.0 * 0.35, t.h4, 0.0001)
    }

    // ── HeaderThresholds data shape ───────────────────────────────────────────

    @Test
    fun `thresholds are ordered h1 gt h2 gt h3 gt h4 for large range`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 12.0, max = 24.0, min = 10.0, range = 14.0)
        assertTrue(t.h1 > t.h2, "h1=$t.h1 should be > h2=$t.h2")
        assertTrue(t.h2 > t.h3, "h2=$t.h2 should be > h3=$t.h3")
        assertTrue(t.h3 > t.h4, "h3=$t.h3 should be > h4=$t.h4")
    }

    @Test
    fun `thresholds never exceed max`() {
        val t = TypographicAnalyzer.computeThresholds(avg = 5.0, max = 30.0, min = 4.0, range = 26.0)
        assertTrue(t.h1 <= 30.0)
        assertTrue(t.h2 <= 30.0)
        assertTrue(t.h3 <= 30.0)
        assertTrue(t.h4 <= 30.0)
    }

    @Test
    fun `negative range falls back to all fallbacks`() {
        // Defensive — range negative should not happen but code branches on `> N`
        val t = TypographicAnalyzer.computeThresholds(avg = 10.0, max = 10.0, min = 12.0, range = -2.0)
        assertEquals(10.0 * 0.95, t.h1, 0.0001)
        assertEquals(10.0 * 0.75, t.h2, 0.0001)
        assertEquals(10.0 * 0.50, t.h3, 0.0001)
        assertEquals(10.0 * 0.35, t.h4, 0.0001)
    }
}