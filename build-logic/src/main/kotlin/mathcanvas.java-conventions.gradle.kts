// Every Java module: the Java 25 toolchain, strict compilation, JUnit Platform tests with
// jqwik and AssertJ, google-java-format through Spotless, a one-line test summary per module
// that tools/gates.sh reports, an hour's timeout on every test task, the wall-clock performance
// tests in a task of their own, the unit-tests-only filter of the quick gate profile, and the
// transform that names the automatic module of a third-party jar without one (Hipparchus), so
// that Gradle puts it on the module path.

import java.time.Duration

plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

// Every lint but requires-automatic: requiring an automatic module (Hipparchus, named by the
// AutomaticModuleName transform below; Commons Geometry, named by its manifest) is what a module
// that does means to do.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-requires-automatic", "-Werror"))
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
    // Gradle's default 512 MB is near what the matrix-size and eigen properties allocate; a
    // worker ran out of heap once on 2026-11-02 with no test failing.
    maxHeapSize = "1g"
    // jqwik's failure database goes under build/, not into the module's source tree.
    systemProperty("jqwik.database", "build/jqwik-database")
    addTestListener(ModuleTestSummary(project.name))
    // A stalled test fails the build instead of running until someone kills it. The slowest
    // module's tests take about 18 minutes on the CI runners (engine, on Windows), so an hour
    // leaves more than a threefold margin and changes nothing on a healthy build.
    timeout.set(Duration.ofMinutes(60))
}

// The wall-clock performance tests, named *PerformanceTest, run in their own task, perfTest, over
// the same test source set: their budgets assume an idle development machine, and a loaded or slow
// runner fails them without a regression. test leaves them out, so ./gradlew build and CI never
// run them; tools/gates.sh runs perfTest in the full profile, after the build, as the landing does.
// Its reports go to build/test-results/perfTest and build/reports/tests/perfTest.
tasks.register<Test>("perfTest") {
    description = "Runs the wall-clock performance tests (*PerformanceTest)."
    group = "verification"
    val test = sourceSets["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    include("**/*PerformanceTest.class")
    // A module without performance tests runs none, which is no misconfiguration.
    failOnNoDiscoveredTests = false
    shouldRunAfter(tasks.named("test"))
    // A pass depends on the machine at the moment, so a result is never reused: the task is never
    // up to date and never served from the build cache.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.named<Test>("test") { exclude("**/*PerformanceTest.class") }

// The quick gate profile (tools/gates.sh --quick passes -PquickGate) runs the unit tests only:
// the property classes, named *Properties, are left to the full gate.
if (providers.gradleProperty("quickGate").isPresent) {
    tasks.named<Test>("test") { exclude("**/*Properties.class") }
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("googleJavaFormat").get().requiredVersion)
        removeUnusedImports()
    }
}

// Third-party jars without a module name of their own (no module-info, no Automatic-Module-Name),
// which Gradle would put on the classpath, where a named module cannot read them: each resolves
// through AutomaticModuleName with the module name below in its manifest, so it lands on the module
// path, and jlink's merged-module handling (app/build.gradle.kts) still sees it as non-modular. To
// add a jar, add its file-name prefix and module name here.
val automaticModuleNamed = Attribute.of("mathcanvas.automaticModuleNamed", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(automaticModuleNamed) }
    artifactTypes.getByName("jar") { attributes.attribute(automaticModuleNamed, false) }
    registerTransform(AutomaticModuleName::class) {
        from.attribute(automaticModuleNamed, false)
            .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(automaticModuleNamed, true)
            .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        parameters {
            modules.put("hipparchus-core-", "hipparchus.core")
            modules.put("hipparchus-ode-", "hipparchus.ode")
        }
    }
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(automaticModuleNamed, true)
    }
    // The project's own jars are published with the attribute, so no transform ever runs on them:
    // the jlink plugin resolves the runtime classpath while the configuration cache is stored, before
    // those jars exist, and a transform on a missing jar fails the store ("must exist").
    if (isCanBeConsumed) {
        attributes.attribute(automaticModuleNamed, true)
    }
}
