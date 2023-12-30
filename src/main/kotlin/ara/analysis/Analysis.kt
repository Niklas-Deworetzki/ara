package ara.analysis

import ara.Main
import ara.input.InputAnalysis
import ara.position.InputSource
import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.extensions.routines
import ara.types.Builtins

abstract class Analysis<T> {
    abstract fun runAnalysis(): T

    protected inline fun proceedAnalysis(action: () -> Unit) {
        if (!hasReportedErrors) action()
    }

    protected inline fun andThen(analysisConstructor: () -> Analysis<Unit>) {
        val analysis = analysisConstructor()
        proceedAnalysis { includeAnalysis(analysis) }
    }

    protected fun <R> includeAnalysis(analysis: Analysis<R>): R {
        val result = analysis.runAnalysis()
        if (analysis.hasReportedErrors) {
            analysis.reportedErrors.forEach(::reportError)
        }
        return result
    }


    private val _reportedErrors: MutableList<Message> = mutableListOf()

    val reportedErrors: List<Message> =
        _reportedErrors

    val hasReportedErrors: Boolean
        get() = _reportedErrors.isNotEmpty()


    fun reportError(message: Message): Message {
        _reportedErrors.add(message)
        return message
    }

    fun reportError(message: String): Message {
        return reportError(Message.error(message = message))
    }


    open val isDebugEnabled: Boolean =
        this::class.simpleName in Main.analysisOptions.debugEnabledPasses

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
            andThen { MemoryResourceAnalysis(program) }
            return program
        }
    }

    fun forEachRoutineIn(program: Syntax.Program, action: ForEachRoutine.() -> Unit) {
        for (routine in program.routines) {
            includeAnalysis(ForEachRoutine(this, routine, action))
        }
    }

    class ForEachRoutine(
        private val parent: Analysis<*>,
        val routine: Syntax.RoutineDefinition,
        private val implementation: ForEachRoutine.() -> Unit
    ) : Analysis<Unit>() {
        override val isDebugEnabled: Boolean
            get() = parent.isDebugEnabled

        override fun runAnalysis() =
            implementation()
    }

    companion object {
        fun ofInput(inputSource: InputSource): Analysis<Syntax.Program> =
            ProgramAnalysis(inputSource)
    }
}

