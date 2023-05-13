package ara

import ara.analysis.Analysis
import ara.input.Scanner
import ara.input.Token
import ara.position.InputSource
import ara.reporting.Message
import ara.reporting.MessageFormatter
import org.fusesource.jansi.Ansi
import java.io.File

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        for (path in args) {
            println("Processing input file $path")
            if (analyseProgram(path))
                println("Success!")
        }
    }


    private fun analyseProgram(path: String): Boolean {
        val input = InputSource.fromFile(File(path))
        val analysis = Analysis.ofInput(input)

        analysis.runAnalysis()
        if (analysis.hasReportedErrors) {
            analysis.reportedErrors
                .map(MessageFormatter::format)
                .forEach(::println)
            return false
        }
        return true
    }
}