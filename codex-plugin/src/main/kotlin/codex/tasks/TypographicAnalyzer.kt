package codex.tasks

/**
 * Pure typography analysis — computes dynamic header thresholds (h1/h2/h3/h4)
 * from font-size statistics. Extracted from `ExtractBookStructureTask` (CDX-7-1).
 *
 * Object with no I/O, no Gradle, no PDF dependency — unit-testable in isolation.
 * Each header level uses a dynamic formula when the font-size range exceeds a
 * threshold, otherwise falls back to a fraction of the max size.
 */
object TypographicAnalyzer {

    /** Computes header thresholds from font-size statistics. */
    fun computeThresholds(avg: Double, max: Double, min: Double, range: Double): HeaderThresholds {
        val dynamicH1 = if (range > 1.0) max - range * 0.05 else max * 0.95
        val dynamicH2 = if (range > 2.0) max - range * 0.25 else max * 0.75
        val dynamicH3 = if (range > 3.0) max - range * 0.50 else max * 0.50
        val dynamicH4 = if (range > 4.0) max - range * 0.70 else max * 0.35
        return HeaderThresholds(dynamicH1, dynamicH2, dynamicH3, dynamicH4)
    }
}

/**
 * Header threshold values for h1/h2/h3/h4 detection from font sizes.
 *
 * @property h1 threshold for level-1 headings (largest)
 * @property h2 threshold for level-2 headings
 * @property h3 threshold for level-3 headings
 * @property h4 threshold for level-4 headings (smallest recognized)
 */
data class HeaderThresholds(
    val h1: Double, val h2: Double, val h3: Double, val h4: Double
)