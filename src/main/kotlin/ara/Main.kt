package ara

import ara.analysis.Analysis
import ara.cli.AnalysisOptions
import ara.interpreter.Interpreter
import ara.position.InputSource
import ara.reporting.MessageFormatter
import ara.syntax.Syntax
import picocli.CommandLine.*
import java.io.File
import java.lang.Exception

@Command(
    name = "ara",
    mixinStandardHelpOptions = true,
    version = ["alpha"]
)
object Main {
    @ArgGroup
    var analysisOptions: AnalysisOptions = AnalysisOptions()

    @Parameters(
        paramLabel = "[file]...",
        description = ["Input files for the compiler."]
    )
    var inputFiles: List<String> = emptyList()

    @JvmStatic
    fun main(args: Array<String>) {
        populateCommand(this, *args)
        for (file in inputFiles) {
            println("Processing input file $file")
            analyseProgram(file)?.execute()
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
