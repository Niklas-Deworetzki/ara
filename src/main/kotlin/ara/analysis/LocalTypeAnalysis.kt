package ara.analysis

import ara.Direction
import ara.analysis.type.TypeCheckingMixin
import ara.analysis.type.TypeComputation.Companion.computedType
import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.Syntax.ComparisonOperator.*
import ara.syntax.Typeable
import ara.syntax.extensions.lookupVariableType
import ara.types.Type
import ara.types.extensions.getMembersType
import ara.types.extensions.getReferenceBase
import ara.utils.combineWith

/**
 * Analysis pass used to detect type errors within a routine's instructions.
 */
class LocalTypeAnalysis(private val program: Syntax.Program) : Analysis<Unit>(), TypeCheckingMixin {

    override fun reportTypeError(message: Message): Message =
        reportError(message)

    override fun runAnalysis() = forEachRoutineIn(program) {
        InstructionTypeChecker(routine).check()
        ensureVariablesHaveInstantiatedTypes(routine)
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

                is Syntax.NullReferenceLiteral ->
                    Type.Reference(Type.Variable())

                is Syntax.AllocationExpression ->
                    checkAllocationExpression(this)

                is Syntax.MemberAccess ->
                    checkMemberAccess(this.storage, this.member)

                is Syntax.NamedStorage ->
                    checkNamedStorage(this)

                is Syntax.TypedStorage ->
                    checkTypedStorage(this)

                is Syntax.DereferencedStorage ->
                    checkDereference(this.storage)

                is Syntax.DereferencedMemory ->
                    checkDereference(this.memory)

                is Syntax.MemoryMemberAccess ->
                    checkMemberAccess(this.memory, this.member)

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

            srcType.mustBe(Type.Integer, assignment.src) {
                "Assignment source must be of type ${Type.Integer} as required by arithmetic assignment."
            }
            dstType.mustBe(Type.Integer, assignment.dst) {
                "Assignment destination must be of type ${Type.Integer} as required by arithmetic assignment."
            }

            val arithmeticType = assignment.arithmetic.value.computedType()
            arithmeticType.mustBe(Type.Integer, assignment.arithmetic) {
                "Arithmetic expression must be of type ${Type.Integer} as required by arithmetic assignment."
            }
        }

        private fun checkMultiAssignment(assignment: Syntax.MultiAssignment) {
            if (assignment.dstList.size != assignment.srcList.size) {
                reportError("Assignment must have an equal number of resources on both sides.")
                    .withPositionOf(assignment)
            }

            val shouldProvideDestinationHint: Boolean = assignment.srcList.size > 2
            combineWith(assignment.srcList, assignment.dstList) { src, dst ->
                val srcType = src.computedType()
                val dstType = dst.computedType()

                val message = typesMustBeTheSame(
                    srcType, "Source",
                    dstType, "Destination",
                    src
                ) {
                    "Assignment source and destination must have compatible types."
                }

                if (shouldProvideDestinationHint) {
                    message?.withAdditionalInfo(
                        "Assignment destination is found here:",
                        position = dst.range
                    )
                }
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
            return Type.fromMembers(typedMembers)
        }

        private fun checkAllocationExpression(expression: Syntax.AllocationExpression): Type {
            val valueType = expression.value.computedType()
            return Type.Reference(valueType)
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

        private fun checkMemberAccess(typeable: Typeable, member: Syntax.Identifier): Type {
            val structureType = typeable.computedType()
            val memberType = structureType.getMembersType(member.name)

            if (memberType == null) {
                val message = reportError("Type $structureType does not have a member named ${member}.")
                    .withPositionOf(member)

                val isReferenceToMatchingType = structureType
                    .getReferenceBase()
                    ?.getMembersType(member.name) != null
                if (isReferenceToMatchingType) {
                    message.withAdditionalInfo("Perhaps a dereference is missing?")
                }
                return Type.Variable()
            }
            return memberType
        }

        private fun checkDereference(reference: Typeable): Type {
            val storageType = reference.computedType()
            val referenceBase = storageType.getReferenceBase()

            if (referenceBase == null) {
                reportError("Value must be a reference as required by dereference operator.")
                    .withPositionOf(reference)
                return Type.Variable()
            }
            return referenceBase
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
}