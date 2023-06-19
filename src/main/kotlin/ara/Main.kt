package ara

import ara.analysis.Analysis
import ara.position.InputSource
import ara.reporting.MessageFormatter
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
            for (error in analysis.reportedErrors) {
                println(MessageFormatter.format(error))
                println()
            }

            return false
        }
        return true
    }
}