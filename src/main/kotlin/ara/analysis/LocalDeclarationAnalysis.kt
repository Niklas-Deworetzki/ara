package ara.analysis

import ara.syntax.Syntax
import ara.types.Environment

class LocalDeclarationAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {
    private lateinit var currentScope: Environment

    override fun runAnalysis() = program.definitions
        .filterIsInstance<Syntax.RoutineDefinition>()
        .forEach { routine ->
            routine.localEnvironment = initializeLocalScope(routine)
        }

    private fun initializeLocalScope(routine: Syntax.RoutineDefinition): Environment {
        currentScope = Environment(program.environment)
        val successfulIn = declareFromParameterList(routine.inputParameters)
        val successfulOut = declareFromParameterList(routine.outputParameters)

        if (successfulIn && successfulOut) {
            routine.body.forEach(::declareFromInstruction)
        }
        return currentScope
    }

    private tailrec fun declare(expression: Syntax.ResourceExpression): Unit = when (expression) {
        is Syntax.NamedStorage ->
            currentScope.declareVariable(expression.name)

        is Syntax.TypedStorage ->
            declare(expression.storage)

        is Syntax.MemberAccess ->
            declare(expression.storage)

        is Syntax.IntegerLiteral ->
            Unit
    }


    private fun declareFromParameterList(parameterList: List<Syntax.Parameter>): Boolean {
        val encounteredNames = mutableSetOf<Syntax.Identifier>()
        var encounteredConflicts = 0

        for (parameter in parameterList) {
            if (!encounteredNames.add(parameter.name)) {
                encounteredConflicts++

                reportError(parameter, "Parameter ${parameter.name} was declared multiple times.")
            } else {
                currentScope.declareVariable(parameter.name)
            }
        }

        return encounteredConflicts == 0
    }

    private fun declareFromInstruction(instruction: Syntax.Instruction) = when (instruction) {
        is Syntax.Assignment -> {
            declare(instruction.src)
            declare(instruction.dst)
        }

        is Syntax.Call -> {
            instruction.srcList.forEach(::declare)
            instruction.dstList.forEach(::declare)
        }

        is Syntax.Conditional ->
            Unit

        is Syntax.Unconditional ->
            Unit
    }
}