import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * The display, as a build service with one usage at a time: every module's uiTest task uses it, so
 * two modules' UI tests never open their windows at once within one build, parallel execution or
 * not (tools/ui_tests.sh's lock does the same across builds).
 */
abstract class UiDisplay : BuildService<BuildServiceParameters.None>
