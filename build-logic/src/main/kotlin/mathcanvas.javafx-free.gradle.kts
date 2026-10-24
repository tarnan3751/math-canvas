// The build-time module-boundary check for the modules that must never reference JavaFX
// (engine, cli), wired into check. tools/gates.sh greps for the same token as a second guard.

plugins { java }

val checkJavafxFree =
    tasks.register<JavafxFreeCheck>("checkJavafxFree") {
        group = "verification"
        description = "Fails if this module references JavaFX in its sources or classpaths."
        sources.from(
            fileTree("src/main") { include("**/*.java") },
            fileTree("src/test") { include("**/*.java") },
        )
        for (name in
            listOf(
                "compileClasspath",
                "runtimeClasspath",
                "testCompileClasspath",
                "testRuntimeClasspath",
            )) {
            resolvedModules.put(
                name,
                configurations.named(name).flatMap { configuration ->
                    configuration.incoming.resolutionResult.rootComponent.map(::resolvedModuleIds)
                },
            )
        }
        projectDirectory = layout.projectDirectory
        result = layout.buildDirectory.file("javafx-free/result.txt")
    }

tasks.named("check") { dependsOn(checkJavafxFree) }
