package ara.analysis

import ara.Direction
import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.Syntax.BinaryOperator.*
import ara.syntax.Syntax.ComparisonOperator.*
import ara.types.Environment
import ara.types.Type
import ara.types.Type.Algebra.Companion.evaluate
import ara.types.TypeUnification
import ara.types.TypeUnification.unify
import ara.utils.combineWith

class LocalTypeAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val definedRoutines = program.definitions.filterIsInstance<Syntax.RoutineDefinition>()

        definedRoutines.forEach {
            ParameterListTyper(it).typeParameters()

            val parameterNames = (it.inputParameters + it.outputParameters).map { parameter -> parameter.name }.toSet()
            ensureVariablesHaveInstantiatedTypes(it.localEnvironment, parameterNames)
            it.inputParameterTypes =
                it.inputParameters.map { parameter -> it.localEnvironment.getVariable(parameter.name)!! }
            it.outputParameterTypes =
                it.outputParameters.map { parameter -> it.localEnvironment.getVariable(parameter.name)!! }
        }
        proceedAnalysis { // We can't proceed if there are not inferred types in parameter lists, as they would be instantiated on calls which could cause hard-to-locate errors.
            definedRoutines.forEach {
                InstructionTypeChecker(it).check()
            }
            definedRoutines.forEach {
                val variableNames = it.localEnvironment.variableNames
                ensureVariablesHaveInstantiatedTypes(it.localEnvironment, variableNames.toSet())
            }
        }
    }

    private fun Syntax.Type.computedType(environment: Environment): Type =
        includeAnalysis(TypeComputation(environment, this))


    private fun ensureVariablesHaveInstantiatedTypes(environment: Environment, names: Set<Syntax.Identifier>) {
        val notInstantiatedNames = names.filterNot { environment.getVariable(it)!!.isInstantiated() }
        for (name in notInstantiatedNames) {
            reportError("Type of variable $name cannot be inferred. Perhaps some type annotations are missing?")
                .withPositionOf(name)
        }
    }


    private inner class ParameterListTyper(private val routine: Syntax.RoutineDefinition) {
        fun typeParameters() {
            for (parameter in routine.inputParameters + routine.outputParameters) {
                if (parameter.type == null) continue

                val declaredType = routine.localEnvironment.getVariable(parameter.name)!!
                val computedType = parameter.type.computedType(routine.localEnvironment)

                declaredType.shouldBe(computedType, parameter)
            }
        }
    }

    private inner class InstructionTypeChecker(private val routine: Syntax.RoutineDefinition) {
        fun check() {
            for (instruction in routine.body) {
                when (instruction) {
                    is Syntax.Assignment ->
                        checkAssignment(instruction)

                    is Syntax.Call ->
                        checkCall(instruction)

                    is Syntax.Conditional ->
                        checkConditional(instruction)

                    is Syntax.Unconditional ->
                        Unit
                }
            }
        }

        private fun checkAssignment(assignment: Syntax.Assignment) {
            val srcType = assignment.src.computedType()
            val dstType = assignment.dst.computedType()

            if (assignment.arithmetic == null) {
                srcType.shouldBe(dstType, assignment) {
                    "Source and destination have different types."
                }

            } else {
                srcType.shouldBe(Type.Integer, assignment.src) {
                    "Assignment source is not of type ${Type.Integer} as required by modification statement."
                }
                dstType.shouldBe(Type.Integer, assignment.dst) {
                    "Assignment destination is not of type ${Type.Integer} as required by modification statement."
                }

                val arithmeticType = assignment.arithmetic.value.computedType()
                arithmeticType.shouldBe(Type.Integer, assignment.arithmetic) {
                    "Arithmetic expression is not of type ${Type.Integer} as required by modification statement."
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
                    checkPassedArguments(call.srcList, calledRoutine.inputParameterTypes)
                    checkPassedArguments(call.dstList, calledRoutine.outputParameterTypes)
                }

                Direction.BACKWARD -> {
                    checkPassedArguments(call.srcList, calledRoutine.outputParameterTypes)
                    checkPassedArguments(call.dstList, calledRoutine.inputParameterTypes)
                }
            }
        }

        private fun checkPassedArguments(arguments: List<Syntax.ResourceExpression>, expectedTypes: List<Type>) {
            combineWith(arguments, expectedTypes) { argument, expectedType ->
                argument.computedType().shouldBe(expectedType, argument)
            }
        }

        private fun checkConditional(conditional: Syntax.Conditional) {
            val comparisonType = conditional.condition.computedType()
            comparisonType.shouldBe(Type.Comparison, conditional.condition) {
                "Expression is not of type ${Type.Comparison} as required by conditional instruction."
            }
        }

        private fun Syntax.ResourceExpression.computedType(): Type = when (this) {
            is Syntax.IntegerLiteral ->
                Type.Integer

            is Syntax.MemberAccess -> {
                val structureType = this.storage.computedType()
                val memberType = structureType.getMemberType(this.member.name)
                if (memberType == null) {
                    reportError("Type does not have a member named ${this.member}.")
                        .withPositionOf(this.member)
                    Type.Variable()
                } else memberType
            }

            is Syntax.NamedStorage -> when (val type = routine.localEnvironment.getVariable(this.name)) {
                null -> {
                    reportError("Unknown variable ${this.name}")
                        .withPositionOf(this)
                    Type.Variable()
                }

                else -> type
            }

            is Syntax.TypedStorage -> {
                val storageType = this.storage.computedType()
                val hintedType = this.type.computedType(routine.localEnvironment)
                storageType.shouldBe(hintedType, this)
                hintedType
            }
        }

        private fun Syntax.ConditionalExpression.computedType(): Type = when (this) {
            is Syntax.ComparativeBinary -> {
                val lhsType = this.lhs.computedType()
                val rhsType = this.rhs.computedType()

                when (this.comparator) {
                    LST, LSE, GRT, GRE -> {
                        lhsType.shouldBe(Type.Integer, this.lhs) {
                            "Operand is not of type ${Type.Integer} as required by comparison operator."
                        }
                        rhsType.shouldBe(Type.Integer, this.rhs) {
                            "Operand is not of type ${Type.Integer} as required by comparison operator."
                        }
                        Type.Comparison
                    }

                    EQU, NEQ -> {
                        lhsType.shouldBe(rhsType, this) {
                            "Operands are not of the same type as required by equality operator."
                        }
                        Type.Comparison
                    }
                }
            }
        }

        private fun Syntax.ArithmeticExpression.computedType(): Type = when (this) {
            is Syntax.ArithmeticBinary -> {
                val lhsType = this.lhs.computedType()
                val rhsType = this.rhs.computedType()

                when (this.operator) {
                    ADD, SUB, XOR, MUL, DIV, MOD -> {
                        lhsType.shouldBe(Type.Integer, this.lhs) {
                            "Operand is not of type ${Type.Integer} as required by arithmetic operator."
                        }
                        rhsType.shouldBe(Type.Integer, this.rhs) {
                            "Operand is not of type ${Type.Integer} as required by arithmetic operator."
                        }
                        Type.Integer
                    }
                }
            }

            is Syntax.ArithmeticValue ->
                this.value.computedType()
        }
    }


    private fun Type.shouldBe(expectedType: Type, position: Syntax, messageHint: (() -> String)? = null) {
        val typeError = unify(this, expectedType) ?: return // Return if unification succeeds.

        val messageHints = mutableListOf<String>()
        if (messageHint != null) messageHints.add(messageHint())

        var currentTypeError = typeError
        var foundCause: Boolean
        do {
            foundCause = false
            when (currentTypeError) {
                TypeUnification.Error.RecursiveType ->
                    messageHints.add("Infinite type arising from constraints.")

                is TypeUnification.Error.DifferentStructSize -> {
                    messageHints.add("Structure types with different size.")
                }

                is TypeUnification.Error.DifferentMemberNames -> {
                    val aMemberName = Message.quote(currentTypeError.aMember.name)
                    val bMemberName = Message.quote(currentTypeError.bMember.name)
                    messageHints.add("Structure type members #${currentTypeError.index + 1} $aMemberName and $bMemberName differ.")
                }

                is TypeUnification.Error.DifferentMemberTypes -> {
                    val memberName = Message.quote(currentTypeError.aMember.name)
                    messageHints.add("Structure type members #${currentTypeError.index + 1} $memberName differ.")
                    currentTypeError = currentTypeError.cause
                    foundCause = true
                }

                is TypeUnification.Error.NotUnifiable ->
                    messageHints.add("Type ${currentTypeError.a} is not equal to ${currentTypeError.b}.")
            }
        } while (foundCause)
        val message = messageHints.reduce { a, b ->
            "$a${System.lineSeparator()} cause: $b"
        }
        reportError(message).withPositionOf(position)
    }

    private fun Type.isInstantiated(): Boolean = TypeIsInstantiated.evaluate(this)

    private object TypeIsInstantiated : Type.Algebra<Boolean> {
        override fun builtin(builtin: Type.BuiltinType): Boolean = true

        override fun structure(memberNames: List<String>, memberValues: List<Boolean>): Boolean =
            memberValues.all { it }

        override fun uninitializedVariable(): Boolean = false
    }

    private fun Type.getMemberType(name: String): Type? = when (this) {
        is Type.Structure ->
            members.find { it.name == name }?.type

        else ->
            null
    }
}