package ara.analysis

import ara.Direction
import ara.analysis.TypeComputation.Companion.getMemberType
import ara.analysis.TypeComputation.Companion.unpackReference
import ara.analysis.TypeComputation.Companion.unify
import ara.syntax.Syntax
import ara.syntax.Syntax.BinaryOperator.*
import ara.types.Type
import ara.utils.Collections.combineWith

class LocalTypeAnalysis(
    private val program: Syntax.Program,
    private val routineMap: RoutineMap,
    private val typeMap: TypeMap
) : Analysis<Unit>() {

    override fun runAnalysis() {
        val declaredRoutines = program.declarations.filterIsInstance<Syntax.RoutineDeclaration>()

        declaredRoutines.forEach {
            ParameterTypeInitializer(it).initialize()
        }
        declaredRoutines.forEach {
            InstructionChecker(it).check()
        }
        declaredRoutines.forEach {
            ensureVariablesAreTypes(it)
        }
    }

    private fun ensureVariablesAreTypes(routine: Syntax.RoutineDeclaration) {
        routine.localScope.forEach { (name, type) ->
            if (!type.isInstantiated()) {
                reportError(name, "Type of $name cannot be inferred. Perhaps type annotations are missing?")
            }
        }
    }


    inner class ParameterTypeInitializer(private val routine: Syntax.RoutineDeclaration) {
        fun initialize() {
            for (parameter in routine.inputParameters + routine.outputParameters) {
                if (parameter.type == null) continue

                val declaredType = routine.localScope[parameter.name]!!
                val computedType = computeType(parameter.type)

                checkTypes(parameter, declaredType, computedType)
            }
        }
    }

    inner class InstructionChecker(private val routine: Syntax.RoutineDeclaration) {
        fun check() {
            for (instruction in routine.body) {
                when (instruction) {
                    is Syntax.Assignment ->
                        checkAssignment(instruction)

                    is Syntax.Call ->
                        checkCall(instruction)

                    is Syntax.Conditional ->
                        Unit

                    is Syntax.Unconditional ->
                        Unit
                }
            }
        }

        private fun checkAssignment(assignment: Syntax.Assignment) {
            val srcType = computeType(assignment.src)
            val dstType = computeType(assignment.dst)

            checkTypes(assignment, srcType, dstType)

            if (assignment.arithmetic != null) {
                val arithmeticType = computeType(assignment.arithmetic.value)
                checkTypes(assignment.arithmetic, Type.Integer, arithmeticType)
            }
        }

        private fun checkCall(call: Syntax.Call) {
            val calledRoutine = routineMap[call.routine]
            if (calledRoutine == null) {
                reportError(call.routine, "Unknown routine ${call.routine}.")
                return
            }

            val (expectedInputs, expectedOutputs) = getExpectedParameters(calledRoutine, call.direction)
            checkPassedArguments(call.srcList, expectedInputs)
            checkPassedArguments(call.dstList, expectedOutputs)
        }

        private fun checkPassedArguments(arguments: List<Syntax.Expression>, expectedTypes: List<Type>) {
            combineWith(arguments, expectedTypes) { argument, expectedType ->
                val actualType = computeType(argument)
                checkTypes(argument, expectedType, actualType)
            }
        }

        private fun getExpectedParameters(
            calledRoutine: Syntax.RoutineDeclaration,
            direction: Direction
        ): Pair<List<Type>, List<Type>> =
            when (direction) {
                Direction.FORWARD -> Pair(
                    calledRoutine.inputParameterTypes,
                    calledRoutine.outputParameterTypes
                )

                Direction.BACKWARD -> Pair(
                    calledRoutine.outputParameterTypes,
                    calledRoutine.inputParameterTypes
                )
            }

        private fun computeType(expression: Syntax.Expression): Type = when (expression) {
            is Syntax.IntegerLiteral ->
                Type.Integer

            is Syntax.AllocationExpression ->
                Type.Reference(computeType(expression.type))

            is Syntax.ReferenceExpression -> {
                val referenceType = computeType(expression.storage)
                val unpackedReference = referenceType.unpackReference()
                if (unpackedReference == null) {
                    reportError(expression, "Cannot load value, since it is not a reference.")
                    Type.Variable()
                } else unpackedReference
            }

            is Syntax.MemberAccess -> {
                val structureType = computeType(expression.storage)
                val memberType = structureType.getMemberType(expression.member.name)
                if (memberType == null) {
                    reportError(expression.member, "Member ${expression.member} is not defined.")
                    Type.Variable()
                } else memberType
            }

            is Syntax.NamedStorage -> {
                val type = routine.localScope[expression.name]
                if (type == null) {
                    reportError(expression, "Unknown variable ${expression.name}")
                    Type.Variable()
                } else type
            }

            is Syntax.TypedStorage -> {
                val storageType = computeType(expression.storage)
                val hintedType = computeType(expression.type)
                checkTypes(expression, hintedType, storageType)
                hintedType
            }
        }

        private fun computeType(expression: Syntax.ArithmeticExpression): Type = when (expression) {
            is Syntax.ArithmeticBinary -> {
                val lhsType = computeType(expression.lhs)
                val rhsType = computeType(expression.rhs)

                when (expression.operator) {
                    ADD, SUB, XOR, MUL, DIV, MOD -> {
                        checkTypes(expression.lhs, Type.Integer, lhsType)
                        checkTypes(expression.rhs, Type.Integer, rhsType)
                        Type.Integer
                    }

                    LST, LSE, GRT, GRE -> {
                        checkTypes(expression.lhs, Type.Integer, lhsType)
                        checkTypes(expression.rhs, Type.Integer, rhsType)
                        Type.Comparison
                    }

                    EQU, NEQ -> {
                        checkTypes(expression, lhsType, rhsType)
                        Type.Comparison
                    }
                }
            }

            is Syntax.ArithmeticValue ->
                computeType(expression.value)
        }
    }

    private fun computeType(type: Syntax.Type): Type {
        val context = TypeComputation(typeMap, type)
        val result = context.runAnalysis()

        context.reportedErrors
            .forEach(::reportError)

        return result
    }

    private fun checkTypes(syntax: Syntax, expectedType: Type, actualType: Type) {
        val typeError = unify(actualType, expectedType) ?: return
        when (typeError) {
            TypeComputation.RecursiveType ->
                reportError(syntax, "Unable to construct infinite type.")

            is TypeComputation.DifferentMemberNames ->
                reportError(syntax, "Incompatible structure types. Fields #${typeError.index} differ in name.")

            is TypeComputation.DifferentMemberTypes ->
                reportError(syntax, "Incompatible structure types. Fields #${typeError.index} differ in type.")

            is TypeComputation.DifferentStructSize ->
                reportError(syntax, "Incompatible structure types of different size.")

            is TypeComputation.NotUnifiable ->
                reportError(syntax, "Incompatible types.")
        }
    }
}