package codex.tasks

import codex.ontology.OntologyDeriver
import codex.ontology.OntologyMapping
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Derives an [OntologyMapping] from the SQL LMD script produced by
 * [ImportBookSqlTask] and the LDD nodes produced by
 * [AsciiDocToJsonLddTask].
 *
 * The computed mapping exposes three archetypal views (P1-19 spec):
 * bounded contexts (FK connectivity), aggregates (CASCADE roots),
 * and value objects (tables without outgoing FK). The result is
 * written as JSON for downstream consumers (knowledge graph,
 * documentation, DDD analysis).
 *
 * @property sqlFile input SQL DDL/INSERT script (output of `collectBookSql`).
 * @property jsonFile input JSON LDD file (output of `transformToJsonLdd`).
 * @property outputFile output JSON ontology mapping file.
 */
@DisableCachingByDefault(because = "SQL LMD → ontology mapping — deterministic pure computation, non-cacheable")
abstract class DeriveOntologyTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sqlFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jsonFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun derive() {
        val sqlInput = sqlFile.asFile.get()
        val lddInput = jsonFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] deriveOntology : ${sqlInput.name} + ${lddInput.name} -> ${output.name}")

        val json = Json { ignoreUnknownKeys = true }
        val lddNodes = json.decodeFromString(ListSerializer(LddNode.serializer()), lddInput.readText())

        val mapping = OntologyDeriver.derive(lddNodes, sqlInput.readText())

        val mappingJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
        output.writeText(mappingJson.encodeToString(OntologyMapping.serializer(), mapping))

        logger.lifecycle(
            "[codex] ontology : ${mapping.boundedContexts.size} bounded contexts, " +
                "${mapping.aggregates.size} aggregates, ${mapping.valueObjects.size} value objects"
        )
    }
}