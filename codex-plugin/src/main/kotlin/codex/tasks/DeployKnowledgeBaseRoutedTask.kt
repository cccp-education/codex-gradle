package codex.tasks

import codex.LicenseZone
import codex.licence.LicenseRouter
import codex.licence.PdfLicenseDetector
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Routes knowledge base export to `OSS/` or `office/` based on the
 * license detected in the source PDF.
 *
 * This task wraps the existing [ExportKnowledgeBaseTask] export logic:
 *  1. Detect the license zone from the PDF content with
 *     [PdfLicenseDetector] (content-based; additive to the path-based
 *     [codex.LicenseZoneDetector]).
 *  2. If the content yields [LicenseZone.UNKNOWN], fall back to the
 *     configured [fallbackZone] (typically the project's path-based zone).
 *  3. Resolve the physical output directory with [LicenseRouter] under
 *     [baseDir] (`baseDir/OSS/` or `baseDir/office/`).
 *  4. Delegate to [ExportKnowledgeBaseTask.export] which writes the
 *     three knowledge-base files (JSON-LD, Markdown, AsciiDoc) into the
 *     resolved directory.
 *
 * [ExportKnowledgeBaseTask] is left unchanged (backward compat): this
 * task is purely *additive* — it rebinds [outputDir] to the routed
 * location before delegating.
 */
@DisableCachingByDefault(because = "Knowledge base routed export — non-cacheable (license detection + multi-format write)")
abstract class DeployKnowledgeBaseRoutedTask : ExportKnowledgeBaseTask() {

    /** Source PDF scanned for license markers (Apache/CC/copyright). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pdfFile: RegularFileProperty

    /**
     * Base directory under which zone subdirectories (`OSS/`, `office/`)
     * are resolved by [LicenseRouter].
     */
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val baseDir: DirectoryProperty

    /**
     * Fallback license zone used when the PDF content yields
     * [LicenseZone.UNKNOWN]. Defaults to [LicenseZone.UNKNOWN]
     * (which routes to `office/` per [LicenseRouter]).
     */
    @get:org.gradle.api.tasks.Input
    @get:Optional
    abstract val fallbackZone: Property<LicenseZone>

    override fun export() {
        val pdf = pdfFile.asFile.get()
        val base = baseDir.asFile.get()
        val fallback = fallbackZone.getOrElse(LicenseZone.UNKNOWN)

        val detected = PdfLicenseDetector.detect(pdf)
        val effective = if (detected == LicenseZone.UNKNOWN) fallback else detected

        val routed = LicenseRouter.route(effective, base)
        routed.mkdirs()
        outputDir.set(routed)

        logger.lifecycle("[codex] deployKnowledgeBaseRouted : detected=$detected effective=$effective → ${routed.absolutePath}")

        super.export()
    }
}