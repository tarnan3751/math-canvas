pluginManagement { includeBuild("build-logic") }

// A settings plugins {} block cannot read the version catalog, so this one version is written
// here rather than in gradle/libs.versions.toml.
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "math-canvas"

include("engine", "render", "canvas", "app", "cli")
