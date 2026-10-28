// Every Java module: the Java 25 toolchain, strict compilation, JUnit Platform tests with
// jqwik and AssertJ, google-java-format through Spotless, a one-line test summary per module
// that tools/gates.sh reports, and an hour's timeout on every test task.

import java.time.Duration

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
    // The smallest default thread stack of the supported platforms (Linux and Windows x64), so a
    // test that recurses too deeply fails here, not only on another platform.
    jvmArgs("-Xss1m")
    // jqwik's failure database goes under build/, not into the module's source tree.
    systemProperty("jqwik.database", "build/jqwik-database")
    addTestListener(ModuleTestSummary(project.name))
    // A stalled test fails the build instead of running until someone kills it. The slowest
    // module's tests take about 18 minutes on the CI runners (engine, on Windows), so an hour
    // leaves more than a threefold margin and changes nothing on a healthy build.
    timeout.set(Duration.ofMinutes(60))
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("googleJavaFormat").get().requiredVersion)
        removeUnusedImports()
    }
}
