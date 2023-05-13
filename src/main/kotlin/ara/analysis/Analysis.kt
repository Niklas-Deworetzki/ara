package ara.analysis

import ara.input.Parser
import ara.input.Scanner
import ara.position.InputSource
import ara.position.Range
import ara.reporting.Message
import ara.syntax.Syntax
import ara.types.Builtins
import org.fusesource.jansi.Ansi

abstract class Analysis<T> {
    abstract fun runAnalysis(): T

    protected inline fun proceedAnalysis(action: () -> Unit) {
        if (!hasReportedErrors) action()
    }

    fun <R> includeAnalysis(analysis: Analysis<R>): R {
        val result = analysis.runAnalysis()
        if (analysis.hasReportedErrors) {
            analysis.reportedErrors.forEach(::reportError)
        }
        return result
    }


    private val _reportedErrors: MutableList<Message> = mutableListOf()

    val reportedErrors: List<Message>
        get() = _reportedErrors

    val hasReportedErrors: Boolean
        get() = _reportedErrors.isNotEmpty()

    private fun mkErrorMessage(message: String, range: Range?): Message = Message(
        Ansi.Color.RED,
        "Error",
        message,
        range
    )

    fun reportError(message: Message) {
        _reportedErrors.add(message)
    }

    fun reportError(message: String) =
        reportError(mkErrorMessage(message, null))

    fun reportError(range: Range, message: String) =
        reportError(mkErrorMessage(message, range))

    fun reportError(syntax: Syntax, message: String) =
        reportError(mkErrorMessage(message, syntax.range))


    private class ProgramAnalysis(val input: InputSource) : Analysis<Syntax.Program>() {
        override fun runAnalysis(): Syntax.Program {
            val program = loadProgram()
            program.environment = Builtins.environment()

            includeAnalysis(RoutineDefinitionAnalysis(program))
            includeAnalysis(ControlGraphBuilder(program))
            includeAnalysis(TypeDefinitionAnalysis(program))
            includeAnalysis(LocalDeclarationAnalysis(program))
            includeAnalysis(LocalTypeAnalysis(program))
            return program
        }

        private fun loadProgram(): Syntax.Program = Scanner(input).use {
            includeAnalysis(Parser(it))
        }
    }

    companion object {

        fun ofInput(inputSource: InputSource): Analysis<Syntax.Program> =
            ProgramAnalysis(inputSource)
    }
}

