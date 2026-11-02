import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input

/**
 * Names the automatic module of a jar that has neither a module descriptor nor an
 * `Automatic-Module-Name`: Gradle puts such a jar on the classpath, where a named module cannot
 * read it. A jar whose file name starts with a key of [Parameters.modules] is copied with
 * `Automatic-Module-Name` set to the key's value in its manifest, every other entry byte for byte
 * the same, the entries in their order and with their times, so that the copy is the same on every
 * run; any other jar passes through unchanged.
 */
abstract class AutomaticModuleName : TransformAction<AutomaticModuleName.Parameters> {

    interface Parameters : TransformParameters {
        /** The module name for each jar file-name prefix, such as `hipparchus-core-`. */
        @get:Input val modules: MapProperty<String, String>
    }

    @get:InputArtifact abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = input.get().asFile
        val module =
            parameters.modules.get().entries.firstOrNull { file.name.startsWith(it.key) }?.value
        if (module == null) {
            outputs.file(file)
            return
        }
        val out = outputs.file(file.name)
        JarFile(file).use { jar ->
            val manifest = Manifest(jar.manifest)
            manifest.mainAttributes.putValue("Automatic-Module-Name", module)
            JarOutputStream(out.outputStream()).use { o ->
                // in the original order, each with its original time, so the output is the same
                // on every run
                for (entry in jar.entries()) {
                    val copy = ZipEntry(entry.name)
                    copy.time = entry.time
                    o.putNextEntry(copy)
                    if (entry.name == JarFile.MANIFEST_NAME) {
                        manifest.write(o)
                    } else {
                        jar.getInputStream(entry).use { it.copyTo(o) }
                    }
                    o.closeEntry()
                }
            }
        }
    }
}
