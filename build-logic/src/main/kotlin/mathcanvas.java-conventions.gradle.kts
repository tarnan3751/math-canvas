// Every Java module: the Java 25 toolchain, strict compilation, JUnit Platform tests with
// jqwik and AssertJ, google-java-format through Spotless, and a one-line test summary per
// module that tools/gates.sh reports.

plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("jqwik").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // jqwik's failure database goes under build/, not into the module's source tree.
    systemProperty("jqwik.database", "build/jqwik-database")
    addTestListener(ModuleTestSummary(project.name))
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("googleJavaFormat").get().requiredVersion)
        removeUnusedImports()
    }
}
