package ara

import ara.analysis.Analysis
import ara.interpreter.Interpreter
import ara.position.InputSource
import ara.reporting.MessageFormatter
import ara.syntax.Syntax
import java.io.File
import java.lang.Exception

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        for (path in args) {
            println("Processing input file $path")
            analyseProgram(path)?.execute()
        }
    }

    private fun Syntax.Program.execute() = try {
        Interpreter(this).run()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    private fun analyseProgram(path: String): Syntax.Program? {
        val input = InputSource.fromFile(File(path))
        val analysis = Analysis.ofInput(input)

        val program = analysis.runAnalysis()
        if (analysis.hasReportedErrors) {
            for (error in analysis.reportedErrors) {
                println(MessageFormatter.format(error))
                println()
            }
            return null
        }
        return program
    }
}