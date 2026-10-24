import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The module boundary (CLAUDE.md, **Module boundaries**): fails when a module that must stay
 * free of JavaFX references it, through a `requires javafx.*` in its module-info.java, the token
 * `javafx.` in any Java source, or an `org.openjfx` artifact on a classpath.
 */
@CacheableTask
abstract class JavafxFreeCheck : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Every module component each checked configuration resolves, by configuration name. */
    @get:Input abstract val resolvedModules: MapProperty<String, List<String>>

    @get:Internal abstract val projectDirectory: DirectoryProperty

    @get:OutputFile abstract val result: RegularFileProperty

    @TaskAction
    fun check() {
        val root = projectDirectory.get().asFile
        val problems = mutableListOf<String>()
        val requiresJavafx = Regex("""\brequires\s+(?:(?:transitive|static)\s+)*javafx\.""")
        val javafxToken = Regex("""\bjavafx\.""")
        for (file in sources.files.sorted()) {
            val path = file.relativeTo(root).path
            file.readLines().forEachIndexed { index, line ->
                if (file.name == "module-info.java" && requiresJavafx.containsMatchIn(line)) {
                    problems += "$path:${index + 1}: requires a JavaFX module: ${line.trim()}"
                } else if (javafxToken.containsMatchIn(line)) {
                    problems += "$path:${index + 1}: references JavaFX: ${line.trim()}"
                }
            }
        }
        for ((configuration, modules) in resolvedModules.get().toSortedMap()) {
            for (module in modules.filter { it.startsWith("org.openjfx:") }) {
                problems += "$configuration resolves $module"
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "module boundary: this module must not reference JavaFX\n" +
                    problems.joinToString("\n") { "  $it" }
            )
        }
        result.get().asFile.writeText("no JavaFX reference\n")
    }
}

/** The `group:name:version` of every external module in the graph under [root]. */
fun resolvedModuleIds(root: ResolvedComponentResult): List<String> {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (seen.add(component)) {
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { pending += it.selected }
        }
    }
    return seen
        .map { it.id }
        .filterIsInstance<ModuleComponentIdentifier>()
        .map { it.displayName }
        .sorted()
}
