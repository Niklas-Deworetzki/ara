package ara.analysis

import ara.Main
import ara.input.InputAnalysis
import ara.position.InputSource
import ara.reporting.Message
import ara.syntax.Syntax
import ara.types.Builtins

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


    fun reportError(message: Message): Message {
        _reportedErrors.add(message)
        return message
    }

    fun reportError(message: String): Message {
        return reportError(Message.error(message = message))
    }


    open val isDebugEnabled: Boolean
        get() = this::class.simpleName in Main.analysisOptions.debugEnabledPasses

    protected inline fun debug(message: () -> String) {
        if (isDebugEnabled)
            System.err.println(message())
    }

    private class ProgramAnalysis(val input: InputSource) : Analysis<Syntax.Program>() {
        override fun runAnalysis(): Syntax.Program {
            val program = includeAnalysis(InputAnalysis(input))
            program.environment = Builtins.environment()

            andThen { RoutineDefinitionAnalysis(program) }
            andThen { ControlGraphBuilder(program) }
            andThen { TypeDefinitionAnalysis(program) }
            andThen { LocalDeclarationAnalysis(program) }
            andThen { ParameterTypeAnalysis(program) }
            andThen { LocalTypeAnalysis(program) }
            andThen { LivenessAnalysis(program) }
            return program
        }


        private inline fun andThen(analysisConstructor: () -> Analysis<Unit>) {
            val analysis = analysisConstructor()
            proceedAnalysis { includeAnalysis(analysis) }
        }
    }

    companion object {
        fun ofInput(inputSource: InputSource): Analysis<Syntax.Program> =
            ProgramAnalysis(inputSource)
    }
}

