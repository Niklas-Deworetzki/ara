package ara.analysis

import ara.storage.ResourceAllocation.resources
import ara.syntax.Syntax
import ara.types.Environment

/**
 * Analysis pass collecting all user-defined variables within routines.
 *
 * Variables are either defined as part of a routine's parameter lists or implicitly
 * as the source or target of an assignment.
 */
class LocalDeclarationAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {
    private lateinit var currentScope: Environment

    override fun runAnalysis() = forEachRoutineIn(program) {
        routine.localEnvironment = initializeLocalScope(routine)
    }

    private fun initializeLocalScope(routine: Syntax.RoutineDefinition): Environment {
        currentScope = Environment(program.environment)
        val inputsDeclaredSuccessfully = declareFromParameterList(routine.inputParameters)
        val outputsDeclaredSuccessfully = declareFromParameterList(routine.outputParameters)

        if (inputsDeclaredSuccessfully && outputsDeclaredSuccessfully) {
            routine.body.forEach(::declareFromInstruction)
        }
        return currentScope
    }

    private tailrec fun declare(expression: Syntax.ResourceExpression): Unit = when (expression) {
        is Syntax.NamedStorage ->
            currentScope.declareVariable(expression.name)

        is Syntax.TypedStorage ->
            declare(expression.storage)

        else ->
            Unit
    }


    private fun declareFromParameterList(parameterList: List<Syntax.Parameter>): Boolean {
        val encounteredNames = mutableSetOf<Syntax.Identifier>()
        var encounteredConflicts = 0

        for (parameter in parameterList) {
            if (!encounteredNames.add(parameter.name)) {
                encounteredConflicts++

                reportError("Parameter ${parameter.name} was declared multiple times.")
                    .withPositionOf(parameter)
            } else {
                currentScope.declareVariable(parameter.name)
            }
        }

        return encounteredConflicts == 0
    }

    private fun declareFromInstruction(instruction: Syntax.Instruction) =
        instruction.resources().forEach(::declare)
}