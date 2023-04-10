package ara.analysis

import ara.syntax.Syntax
import ara.types.Type

class LocalDeclarationAnalysis(override val program: Syntax.Program) : Analysis<Unit>() {
    private lateinit var currentScope: MutableMap<Syntax.Identifier, Type>

    override fun runAnalysis() = program.declarations
        .filterIsInstance<Syntax.RoutineDeclaration>()
        .forEach { routine ->
            routine.localScope = initializeLocalScope(routine)
        }

    private fun initializeLocalScope(routine: Syntax.RoutineDeclaration): Map<Syntax.Identifier, Type> {
        currentScope = mutableMapOf()
        val successfulIn = declareFromParameterList(routine.inputParameters)
        val successfulOut = declareFromParameterList(routine.outputParameters)

        if (successfulIn && successfulOut) {
            routine.body.forEach(::declareFromInstruction)
        }
        return currentScope
    }

    private fun declare(name: Syntax.Identifier) {
        currentScope.computeIfAbsent(name) {
            Type.Variable()
        }
    }

    private tailrec fun declare(expression: Syntax.Expression): Unit = when (expression) {
        is Syntax.NamedStorage ->
            declare(expression.name)


        is Syntax.ReferenceExpression ->
            declare(expression.storage)

        is Syntax.TypedStorage ->
            declare(expression.storage)

        is Syntax.MemberAccess ->
            declare(expression.storage)


        is Syntax.IntegerLiteral ->
            Unit

        is Syntax.AllocationExpression ->
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
                declare(parameter.name)
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