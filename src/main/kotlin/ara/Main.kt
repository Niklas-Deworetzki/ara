package ara

import ara.analysis.Analysis
import ara.cli.AnalysisOptions
import ara.cli.ApplicationOptions
import ara.cli.BuildInfo
import ara.interpreter.Interpreter
import ara.position.InputSource
import ara.reporting.MessageFormatter
import ara.syntax.Syntax
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess


@Command(
    name = "ara",
    synopsisHeading = "%n",
    abbreviateSynopsis = true,

    descriptionHeading = "%n",
    description = [
        "An experimental compiler and interpreter for the advanced reversible assembly language ARA."
    ],

    parameterListHeading = "%nParameters:%n",
    footerHeading = "%n",
    footer = [
        "Copyright (C) 2023 Niklas Deworetzki"
    ],
    usageHelpAutoWidth = true
)
object Main {
    @Parameters(
        paramLabel = "INPUT",
        description = ["Source file used as input for the compiler."]
    )
    lateinit var inputFile: String

    @ArgGroup(
        validate = false,
        heading = "%nDebug & analysis options:%n"
    )
    var analysisOptions: AnalysisOptions = AnalysisOptions()

    @ArgGroup(
        validate = false,
        heading = "%nApplication options:%n"
    )
    var applicationOptions: ApplicationOptions = ApplicationOptions()

    private const val EXIT_SUCCESS = 0
    private const val EXIT_COMPILE_ERROR = 1
    private const val EXIT_USER_ERROR = 2
    private const val EXIT_FATAL = 70
    private const val EXIT_IO_ERROR = 74

    private fun displayVersion() {
        println("ara compiler version ${BuildInfo.version}, build time ${BuildInfo.buildTime}")
    }

    private fun displayHelp(cli: CommandLine) {
        cli.usage(System.out)
    }

    private fun execute(args: Array<String>): Int {
        val cli = CommandLine(this)
        try {
            cli.parseArgs(*args)

            if (applicationOptions.requestedVersionInfo)
                displayVersion()
            if (applicationOptions.requestedHelp)
                displayHelp(cli)
            if (applicationOptions.requestedVersionInfo || applicationOptions.requestedHelp)
                return EXIT_SUCCESS

            val analysedProgram = analyseProgram(inputFile)
                ?: return EXIT_COMPILE_ERROR
            Interpreter(analysedProgram).run()
        } catch (cliException: ParameterException) {
            if (args.isEmpty()) { // Display help if no arguments have been provided.
                displayHelp(cli)
            } else {
                println(cliException.message)
            }
            return EXIT_USER_ERROR

        } catch (ioException: IOException) {
            val exceptionMessage = ioException.message ?: ioException.toString()
            println(exceptionMessage)
            return EXIT_IO_ERROR

        } catch (fatal: Exception) {
            fatal.printStackTrace()
            return EXIT_FATAL
        }
        return EXIT_SUCCESS
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val exitCode = execute(args)
        exitProcess(exitCode)
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
