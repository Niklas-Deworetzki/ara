package ara

import ara.analysis.*
import ara.input.Parser
import ara.input.Scanner
import ara.position.InputSource
import ara.reporting.MessageFormatter
import ara.types.Builtins
import java.io.File

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        for (path in args) {
            try {
                println("Processing input file $path")
                analyseProgram(path)
                println("Success!")
            } catch (detectedErrors: IllegalStateException) {
                println(detectedErrors.message)
            }
        }
    }


    private fun analyseProgram(path: String) {
        val file = File(path)
        val input = InputSource.fromFile(file)

        val program = Scanner(input).use {
            runAnalysis(Parser(it))
        }

        program.environment = Builtins.environment()

        runAnalysis(RoutineDefinitionAnalysis(program))
        runAnalysis(ControlGraphBuilder(program))
        runAnalysis(TypeDefinitionAnalysis(program))
        runAnalysis(LocalDeclarationAnalysis(program))
        runAnalysis(LocalTypeAnalysis(program))
    }

    private fun <T> runAnalysis(analysis: Analysis<T>): T {
        val result = analysis.runAnalysis()
        if (analysis.hasReportedErrors) {
            analysis.reportedErrors
                .map(MessageFormatter::format)
                .forEach(::println)
            throw IllegalStateException("Encountered errors during analysis.")
        }
        return result
    }
}