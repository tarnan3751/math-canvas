// The build-time module-boundary check, wired into check: a module applying it with no packages
// (engine, cli) must never reference JavaFX, in its sources or its classpaths; one that lists
// packages (javafxFree { packages = listOf(...) }: render, canvas) depends on JavaFX itself, and
// only those packages' main and test sources are checked. tools/gates.sh reports its result.

plugins { java }

val javafxFree = extensions.create<JavafxFreeExtension>("javafxFree")

javafxFree.packages.convention(emptyList())

val checkJavafxFree =
    tasks.register<JavafxFreeCheck>("checkJavafxFree") {
        group = "verification"
        description = "Fails if this module, or the packages it lists, reference JavaFX."
        val packages = javafxFree.packages
        sources.from(
            packages.map { listed ->
                if (listed.isEmpty()) {
                    listOf("src/main", "src/test").map { fileTree(it) { include("**/*.java") } }
                } else {
                    listOf("main", "test").flatMap { set ->
                        listed.map { pkg ->
                            val dir = "src/$set/java/${pkg.replace('.', '/')}"
                            fileTree(dir) { include("**/*.java") }
                        }
                    }
                }
            }
        )
        for (name in
            listOf(
                "compileClasspath",
                "runtimeClasspath",
                "testCompileClasspath",
                "testRuntimeClasspath",
            )) {
            resolvedModules.putAll(
                packages.flatMap { listed ->
                    if (listed.isEmpty()) {
                        configurations.named(name).flatMap { configuration ->
                            configuration.incoming.resolutionResult.rootComponent.map {
                                mapOf(name to resolvedModuleIds(it))
                            }
                        }
                    } else {
                        provider { emptyMap<String, List<String>>() }
                    }
                }
            )
        }
        projectDirectory = layout.projectDirectory
        result = layout.buildDirectory.file("javafx-free/result.txt")
    }

tasks.named("check") { dependsOn(checkJavafxFree) }
