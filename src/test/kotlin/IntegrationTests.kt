import ara.analysis.Analysis
import ara.position.InputSource
import ara.reporting.MessageFormatter
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.nio.file.Files
import kotlin.test.fail

private const val SUCCESS_HEADER = "// Success"
private const val ERROR_HEADER = "// Error: "

class IntegrationTests {

    @TestFactory
    fun verifyAllTestPrograms(): DynamicNode =
        createTestsFromFile(File("tests"))

    private fun createTestsFromFile(file: File): DynamicNode = when {
        file.isDirectory -> {
            val subTests = file.listFiles()!!.map { createTestsFromFile(it) }
            DynamicContainer.dynamicContainer(file.name, subTests)
        }

        else ->
            DynamicTest.dynamicTest(file.name) {
                verifyTestProgram(file)
            }
    }

    private fun verifyTestProgram(file: File) {
        val expectedResult = parseExpectedResult(file)
        val analysis = Analysis.ofInput(InputSource.fromFile(file))

        analysis.runAnalysis()
        for (error in analysis.reportedErrors) {
            println(MessageFormatter.format(error))
        }

        when (expectedResult) {
            Success ->
                assert(!analysis.hasReportedErrors) {
                    "Analysis should not have reported errors, but ${analysis.reportedErrors.size} were found!"
                }

            is Error ->
                assert(analysis.reportedErrors.any { it.message.lines().first() == expectedResult.message }) {
                    "Analysis should report at least one error with message: ${expectedResult.message}"
                }
        }
    }

    private fun parseExpectedResult(file: File): ExpectedResult {
        val lines = Files.readAllLines(file.toPath())
        assert(lines.size >= 1) {
            "Header line must be present in ${file.absolutePath}."
        }

        val header = lines.first()
        return when {
            header.startsWith(SUCCESS_HEADER) -> Success
            header.startsWith(ERROR_HEADER) -> Error(header.drop(ERROR_HEADER.length))
            else -> fail("Invalid header line: $header")
        }
    }

    sealed class ExpectedResult
    object Success : ExpectedResult()
    data class Error(val message: String) : ExpectedResult()
}