package ara.analysis

import ara.Direction
import ara.analysis.type.TypeCheckingMixin
import ara.analysis.type.TypeComputation.Companion.computedType
import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.Syntax.ComparisonOperator.*
import ara.syntax.Typeable
import ara.syntax.extensions.lookupVariableType
import ara.syntax.extensions.routines
import ara.types.Type
import ara.utils.combineWith

/**
 * Analysis pass used to detect type errors within a routine's instructions.
 */
class LocalTypeAnalysis(private val program: Syntax.Program) : Analysis<Unit>(), TypeCheckingMixin {

    override fun reportTypeError(message: Message): Message =
        reportError(message)

    override fun runAnalysis() {
        for (routine in program.routines) {
            InstructionTypeChecker(routine).check()
            ensureVariablesHaveInstantiatedTypes(routine)
        }
    }

    private fun ensureVariablesHaveInstantiatedTypes(routine: Syntax.RoutineDefinition) {
        val variableNames = routine.localEnvironment.variableNames.toSet()
        variableNames.ensureInstantiated(routine, "variable")
    }

    private inner class InstructionTypeChecker(private val routine: Syntax.RoutineDefinition) {
        fun check() {
            for (instruction in routine.body) {
                when (instruction) {
                    is Syntax.ArithmeticAssignment ->
                        checkArithmeticAssignment(instruction)

                    is Syntax.MultiAssignment ->
                        checkMultiAssignment(instruction)

                    is Syntax.Call ->
                        checkCall(instruction)

                    is Syntax.Conditional ->
                        checkConditional(instruction)

                    is Syntax.Unconditional ->
                        Unit
                }
            }
        }

        private fun Typeable.computedType(): Type {
            this.computedType = when (this) {
                is Syntax.IntegerLiteral ->
                    Type.Integer

                is Syntax.StructureLiteral ->
                    checkStructureLiteral(this)

                is Syntax.MemberAccess ->
                    checkMemberAccess(this)

                is Syntax.NamedStorage ->
                    checkNamedStorage(this)

                is Syntax.TypedStorage ->
                    checkTypedStorage(this)

                is Syntax.ArithmeticValue ->
                    this.value.computedType()

                is Syntax.ArithmeticBinary ->
                    checkArithmeticBinary(this)

                is Syntax.ComparativeBinary ->
                    checkComparativeBinary(this)
            }
            return this.computedType
        }

        private fun checkArithmeticAssignment(assignment: Syntax.ArithmeticAssignment) {
            val srcType = assignment.src.computedType()
            val dstType = assignment.dst.computedType()

            if (assignment.arithmetic == null) {
                typesMustBeTheSame(
                    srcType, "Source",
                    dstType, "Destination",
                    assignment
                ) {
                    "Assignment source and destination must have compatible types."
                }

            } else {
                srcType.mustBe(Type.Integer, assignment.src) {
                    "Assignment source must be of type ${Type.Integer} as required by arithmetic modifier."
                }
                dstType.mustBe(Type.Integer, assignment.dst) {
                    "Assignment destination must be of type ${Type.Integer} as required by arithmetic modifier."
                }

                val arithmeticType = assignment.arithmetic.value.computedType()
                arithmeticType.mustBe(Type.Integer, assignment.arithmetic) {
                    "Arithmetic expression must be of type ${Type.Integer} as required by arithmetic assignment."
                }
            }
        }

        private fun checkMultiAssignment(assignment: Syntax.MultiAssignment) {
            if (assignment.dstList.size != assignment.srcList.size) {
                reportError("Assignment must have an equal number of resources on both sides.")
                    .withPositionOf(assignment)
            }

            combineWith(assignment.srcList, assignment.dstList) { src, dst ->
                val srcType = src.computedType()
                val dstType = dst.computedType()

                typesMustBeTheSame(
                    srcType, "Source",
                    dstType, "Destination",
                    src
                ) {
                    "Assignment source and destination must have compatible types."
                }?.withAdditionalInfo(
                    "Assignment destination is found here:",
                    position = dst.range
                )
            }
        }

        private fun checkCall(call: Syntax.Call) {
            val calledRoutine = routine.localEnvironment.getRoutine(call.routine)
            if (calledRoutine == null) {
                reportError("Unknown routine ${call.routine}.")
                    .withPositionOf(call.routine)
                return
            }

            when (call.direction) {
                Direction.FORWARD -> {
                    checkPassedArguments(call.srcList, calledRoutine.signature.inputTypes)
                    checkPassedArguments(call.dstList, calledRoutine.signature.outputTypes)
                }

                Direction.BACKWARD -> {
                    checkPassedArguments(call.srcList, calledRoutine.signature.outputTypes)
                    checkPassedArguments(call.dstList, calledRoutine.signature.inputTypes)
                }
            }
        }

        private fun checkPassedArguments(arguments: List<Syntax.ResourceExpression>, expectedTypes: List<Type>) =
            combineWith(arguments, expectedTypes) { argument, expectedType ->
                val argumentType = argument.computedType()
                argumentType.mustBe(expectedType, argument) {
                    "Argument type must be compatible with defined parameter."
                }
            }

        private fun checkConditional(conditional: Syntax.Conditional) {
            val comparisonType = conditional.condition.computedType()
            comparisonType.mustBe(Type.Comparison, conditional.condition) {
                "Expression type must be ${Type.Comparison} as required by conditional instruction."
            }
        }

        private fun checkStructureLiteral(literal: Syntax.StructureLiteral): Type {
            val typedMembers = literal.members.map { member ->
                val memberType = member.value.computedType()
                Type.Member(member.name.name, memberType)
            }
            return Type.Structure(typedMembers)
        }

        private fun checkMemberAccess(memberAccess: Syntax.MemberAccess): Type {
            val structureType = memberAccess.storage.computedType()
            val memberType = structureType.getMemberType(memberAccess.member.name)

            if (memberType == null) {
                reportError("Type $structureType does not have a member named ${memberAccess.member}.")
                    .withPositionOf(memberAccess.member)
                return Type.Variable()
            }
            return memberType
        }

        private fun checkNamedStorage(storage: Syntax.NamedStorage): Type {
            val type = routine.lookupVariableType(storage.name)

            if (type == null) {
                reportError("Unknown variable ${storage.name}.")
                    .withPositionOf(storage)
                return Type.Variable()
            }
            return type
        }

        private fun checkTypedStorage(storage: Syntax.TypedStorage): Type {
            val storageType = storage.storage.computedType()
            val hintedType = computedType(storage.type, routine.localEnvironment)

            storageType.mustBe(hintedType, storage.type) {
                "Type of expression must be compatible with type defined type hint."
            }
            return hintedType
        }

        private fun checkArithmeticBinary(binary: Syntax.ArithmeticBinary): Type {
            val lhsType = binary.lhs.computedType()
            val rhsType = binary.rhs.computedType()

            lhsType.mustBe(Type.Integer, binary.lhs) {
                "Operand must be of type ${Type.Integer} as required by arithmetic operator."
            }
            rhsType.mustBe(Type.Integer, binary.rhs) {
                "Operand must be of type ${Type.Integer} as required by arithmetic operator."
            }
            return Type.Integer
        }

        private fun checkComparativeBinary(binary: Syntax.ComparativeBinary): Type {
            val lhsType = binary.lhs.computedType()
            val rhsType = binary.rhs.computedType()

            when (binary.comparator) {
                LST, LSE, GRT, GRE -> {
                    lhsType.mustBe(Type.Integer, binary.lhs) {
                        "Operand must be of type ${Type.Integer} as required by comparison operator."
                    }
                    rhsType.mustBe(Type.Integer, binary.rhs) {
                        "Operand must be of type ${Type.Integer} as required by comparison operator."
                    }
                }

                EQU, NEQ -> {
                    typesMustBeTheSame(
                        lhsType, "Left operand",
                        rhsType, "Right operand",
                        binary
                    ) {
                        "Operands must be of the same type as required by equality operator."
                    }
                }
            }
            return Type.Comparison
        }
    }

    private tailrec fun Type.getMemberType(name: String): Type? = when (this) {
        is Type.Structure ->
            members.find { it.name == name }?.type

        is Type.Variable ->
            type?.getMemberType(name)

        else ->
            null
    }
}