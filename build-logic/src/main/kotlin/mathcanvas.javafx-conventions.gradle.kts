// The JavaFX modules a presentation module compiles and runs against (render, canvas, app).
// The OpenJFX jars are platform-specific: the classifier comes from the OS and architecture
// running the build. Each is declared non-transitive because the OpenJFX POMs name their
// platform jars through a Maven profile property Gradle does not evaluate; transitively they
// would only add the empty platform-less jars (automatic modules javafx.*Empty), which would
// land on the module path and break jlink.

plugins { java }

val libs = versionCatalogs.named("libs")
val javafxVersion = libs.findVersion("javafx").get().requiredVersion

val osName = providers.systemProperty("os.name").get().lowercase()
val osArch = providers.systemProperty("os.arch").get().lowercase()
val arm = osArch == "aarch64" || osArch == "arm64"
val javafxPlatform =
    when {
        osName.startsWith("mac") -> if (arm) "mac-aarch64" else "mac"
        osName.startsWith("windows") -> "win"
        osName.startsWith("linux") -> if (arm) "linux-aarch64" else "linux"
        else -> error("no OpenJFX build for $osName $osArch")
    }

dependencies {
    for (module in listOf("base", "graphics", "controls")) {
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform") {
            isTransitive = false
        }
    }
}
