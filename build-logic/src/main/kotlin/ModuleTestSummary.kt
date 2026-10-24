import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Prints `<module>: N tests completed, M failed, K skipped` once a module's test run ends, the
 * line tools/gates.sh reports.
 */
class ModuleTestSummary(private val moduleName: String) : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent == null) {
            println(
                "$moduleName: ${result.testCount} tests completed, " +
                    "${result.failedTestCount} failed, ${result.skippedTestCount} skipped"
            )
        }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {}

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
}
