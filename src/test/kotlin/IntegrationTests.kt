import ara.analysis.Analysis
import ara.input.Scanner
import ara.input.Sym
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
                verify(specification)
            }
        }
    }

    private fun verify(specification: TestSpecification) {
        val analysis = Analysis.ofInput(InputSource.fromFile(specification.file))

        analysis.runAnalysis()
        for (error in analysis.reportedErrors) {
            println(MessageFormatter.format(error))
        }

        when (specification) {
            is PositiveTest -> {
                val unexpectedErrors = analysis.reportedErrors
                if (unexpectedErrors.isNotEmpty()) {
                    throw TestFailure(
                        specification,
                        "Analysis should not have reported errors, but ${unexpectedErrors.size} were found.",
                        unexpectedErrors = unexpectedErrors.map { it.message }
                    )
                }
            }

            is NegativeTest -> {
                if (!analysis.hasReportedErrors) {
                    throw TestFailure(
                        specification,
                        "Analysis should report some errors but none were found.",
                        missingErrors = specification.errors
                    )
                }
                if (specification.errors.isEmpty()) {
                    throw TestFailure(
                        specification,
                        "No errors were specified for this test case.",
                        missingErrors = analysis.reportedErrors.map { it.message }
                    )
                }

                val missingErrors = specification.errors
                    .filterNot { error -> analysis.reportedErrors.any { it.message == error } }

                if (missingErrors.isNotEmpty()) {
                    throw TestFailure(
                        specification,
                        "Analysis should report at least ${missingErrors.size} unreported errors.",
                        missingErrors = missingErrors
                    )
                }
            }
        }
    }

    private fun parseTestSpecification(file: File): TestSpecification =
        InputSource.fromFile(file).open().use { reader ->
            val scanner = Scanner(reader)
            val lines = mutableListOf<String>()

            var token = scanner.next_token()
            while (token.sym == Sym.HASHCOMMENT) {
                val comment = (token.value!! as String).trim()
                lines.add(comment)
                token = scanner.next_token()
            }

            val header = lines.firstOrNull()
            when {
                header != null && header.startsWith(POSITIVE_TEST_HEADER) -> {
                    val testDescription = header.substring(POSITIVE_TEST_HEADER.length)
                    PositiveTest(file, testDescription.trim())
                }

                header != null && header.startsWith(NEGATIVE_TEST_HEADER) -> {
                    val testDescription = header.substring(NEGATIVE_TEST_HEADER.length)
                    val expectedMessages = lines.drop(1)
                    NegativeTest(file, testDescription.trim(), expectedMessages)
                }

                else ->
                    PositiveTest(file, null)
            }
        }
}