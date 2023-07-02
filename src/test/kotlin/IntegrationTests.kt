import ara.analysis.Analysis
import ara.input.Scanner
import ara.input.Token
import ara.position.InputSource
import ara.reporting.MessageFormatter
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

private const val POSITIVE_TEST_HEADER = "Success: "
private const val NEGATIVE_TEST_HEADER = "Error: "

class IntegrationTests {

    @TestFactory
    fun verifyAllTestPrograms(): DynamicNode =
        createTestsFromFile(File("tests"))

    private fun createTestsFromFile(file: File): DynamicNode = when {
        file.isDirectory -> {
            val subTests = file.listFiles()!!
                .sortedBy { it.name }
                .map { createTestsFromFile(it) }
            DynamicContainer.dynamicContainer(file.name, subTests)
        }

        else -> {
            val specification = parseTestSpecification(file)
            DynamicTest.dynamicTest(file.name + ": " + specification.description) {
                verifyTestProgram(specification, file)
            }
        }
    }

    private fun verifyTestProgram(specification: TestSpec, file: File) {
        val analysis = Analysis.ofInput(InputSource.fromFile(file))

        analysis.runAnalysis()
        for (error in analysis.reportedErrors) {
            println(MessageFormatter.format(error))
        }

        when (specification) {
            is PositiveTest ->
                assert(!analysis.hasReportedErrors) {
                    "Analysis should not have reported errors, but ${analysis.reportedErrors.size} were found!"
                }

            is NegativeTest -> {
                assert(specification.errors.isNotEmpty()) {
                    "Negative tests must specify at least 1 expected error."
                }

                for (error in specification.errors) {
                    assert(analysis.reportedErrors.any { it.message == error }) {
                        "Analysis should report at least one error with message: $error"
                    }
                }
            }
        }
    }

    private fun parseTestSpecification(file: File): TestSpec =
        Scanner(InputSource.fromFile(file)).use { scanner ->
            val lines = mutableListOf<String>()

            var token = scanner.nextToken()
            while (token.type == Token.Type.COMMENT) {
                val comment = token.value!!.trim()
                lines.add(comment)
                token = scanner.nextToken()
            }

            val header = lines.firstOrNull()
            when {
                header != null && header.startsWith(POSITIVE_TEST_HEADER) ->
                    PositiveTest(header.substring(POSITIVE_TEST_HEADER.length).trim())

                header != null && header.startsWith(NEGATIVE_TEST_HEADER) ->
                    NegativeTest(header.substring(NEGATIVE_TEST_HEADER.length).trim(), lines.drop(1))

                else ->
                    PositiveTest(null)
            }
        }

    sealed class TestSpec(val description: String)
    class PositiveTest(description: String?) : TestSpec(description ?: "A regular program should pass analysis.")
    class NegativeTest(description: String, val errors: List<String>) : TestSpec(description)
}