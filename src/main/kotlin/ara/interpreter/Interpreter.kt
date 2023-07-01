package ara.interpreter

import ara.Direction
import ara.control.Block
import ara.storage.ResourceAllocation.asResourcePath
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.utils.combineWith
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class Interpreter(val program: Syntax.Program) : Runnable {
    private val callStack: Deque<StackFrame> = ArrayDeque()

    val currentStackFrame: StackFrame
        get() = callStack.first

    override fun run() {
        TODO("Start execution of main routine")
    }

    private inner class RoutineExecutor(
        val routine: Syntax.RoutineDefinition,
        val direction: Direction
    ) {
        private val instructions: Queue<Syntax.Instruction> = ArrayDeque()
        private lateinit var lastJumpTarget: Syntax.Identifier
        private lateinit var currentInstruction: Syntax.Instruction

        fun executeWith(arguments: List<Value>): List<Value> {
            initializeInputs(arguments)

            val firstBlock = direction.choose(routine.graph.beginBlock, routine.graph.endBlock)
            enqueueForExecution(firstBlock)
            while (!instructions.isEmpty()) {
                currentInstruction = instructions.remove()
                executeInstruction(currentInstruction)
            }

            return finalizeOutputs()
        }

        private fun executeInstruction(instruction: Syntax.Instruction) = when (instruction) {
            is Syntax.Assignment -> {
                val finalized = direction.choose(instruction.src, instruction.dst)
                val initialized = direction.choose(instruction.dst, instruction.src)

                if (instruction.arithmetic == null) {
                    val value = finalize(finalized)
                    initialize(initialized, value)
                } else {
                    val value = finalize(finalized).asInteger()
                    val modifiedValue = applyModification(value, instruction.arithmetic)
                    initialize(initialized, modifiedValue)
                }
            }

            is Syntax.Call -> {
                val routine = program.environment.getRoutine(instruction.routine)
                internalAssertion(routine != null) {
                    "Routine ${instruction.routine} was called but is not defined."
                }

                val finalized = direction.choose(instruction.srcList, instruction.dstList)
                val initialized = direction.choose(instruction.dstList, instruction.srcList)
                val effectiveCallDirection = getEffectiveDirection(instruction.direction)

                val arguments = finalized.map(::finalize)
                val results = executeCalledRoutine(effectiveCallDirection, routine, arguments, instruction)
                combineWith(initialized, results) { resource, result ->
                    initialize(resource, result)
                }
            }

            is Syntax.Conditional -> {
                val condition = evaluateCondition(instruction.condition)
                val target = condition.choose(instruction.lhsLabel, instruction.rhsLabel)
                executeControlFlow(instruction.direction, target)
            }

            is Syntax.Unconditional ->
                executeControlFlow(instruction.direction, instruction.label)
        }

        private fun getEffectiveDirection(direction: Direction): Direction =
            this.direction.choose(direction, direction.inverted)

        private fun executeCalledRoutine(
            effectiveDirection: Direction,
            routine: Syntax.RoutineDefinition,
            arguments: List<Value>,
            caller: Syntax.Call? = null
        ): List<Value> {
            callStack.push(StackFrame(routine, caller))
            val results = RoutineExecutor(routine, effectiveDirection)
                .executeWith(arguments)
            callStack.pop()
            return results
        }

        private fun executeControlFlow(direction: Direction, label: Syntax.Identifier) {
            when (getEffectiveDirection(direction)) {
                Direction.FORWARD -> {
                    val targetBlock = routine.graph.getBlockByLabel(label, direction)
                    enqueueForExecution(targetBlock)
                    lastJumpTarget = label
                }

                Direction.BACKWARD ->
                    ensureReversibility(lastJumpTarget == label) {
                        "Jump targeting $lastJumpTarget was accepted by $label instead!"
                    }
            }
        }

        private fun enqueueForExecution(block: Block) {
            val orderedInstructions = when (direction) {
                Direction.FORWARD -> block
                Direction.BACKWARD -> block.reversed()
            }
            instructions.addAll(orderedInstructions)
        }


        fun applyModification(value: Int, modifier: Syntax.ArithmeticModifier): Value {
            val modification = evaluate(modifier.value)
            val effectiveOperator = direction.choose(
                modifier.modificationOperator,
                modifier.modificationOperator.invert()
            )

            val intValue = when (effectiveOperator) {
                Syntax.ModificationOperator.ADD -> value + modification
                Syntax.ModificationOperator.SUB -> value - modification
                Syntax.ModificationOperator.XOR -> value xor modification
            }
            return Value.Integer(intValue)
        }

        fun evaluate(expression: Syntax.ArithmeticExpression): Int = when (expression) {
            is Syntax.ArithmeticValue ->
                evaluate(expression.value).asInteger()

            is Syntax.ArithmeticBinary -> {
                val lhs = evaluate(expression.lhs).asInteger()
                val rhs = evaluate(expression.rhs).asInteger()

                if (expression.operator in setOf(Syntax.BinaryOperator.DIV, Syntax.BinaryOperator.MOD)) {
                    if (rhs == 0) {
                        raise(ArithmeticException("Division by 0!"), expression.rhs)
                    }
                }

                when (expression.operator) {
                    Syntax.BinaryOperator.ADD -> lhs + rhs
                    Syntax.BinaryOperator.SUB -> lhs - rhs
                    Syntax.BinaryOperator.XOR -> lhs xor rhs
                    Syntax.BinaryOperator.MUL -> lhs * rhs
                    Syntax.BinaryOperator.DIV -> lhs / rhs
                    Syntax.BinaryOperator.MOD -> lhs % rhs
                }
            }
        }

        fun evaluateCondition(condition: Syntax.ConditionalExpression): Boolean = when (condition) {
            is Syntax.ComparativeBinary -> {
                val lhs = evaluate(condition.lhs)
                val rhs = evaluate(condition.rhs)
                when (condition.comparator) {
                    Syntax.ComparisonOperator.EQU -> lhs == rhs
                    Syntax.ComparisonOperator.NEQ -> lhs != rhs
                    Syntax.ComparisonOperator.LST -> lhs < rhs
                    Syntax.ComparisonOperator.LSE -> lhs <= rhs
                    Syntax.ComparisonOperator.GRT -> lhs > rhs
                    Syntax.ComparisonOperator.GRE -> lhs >= rhs
                }
            }
        }

        fun evaluate(expression: Syntax.ResourceExpression): Value = when (expression) {
            is Syntax.IntegerLiteral -> Value.Integer(expression.value)
            is Syntax.TypedStorage -> evaluate(expression.storage)
            is Syntax.NamedStorage -> currentStackFrame[ResourcePath.ofIdentifier(expression.name)]
            is Syntax.MemberAccess -> {
                val structure = evaluate(expression.storage).asStructure()
                val memberValue = structure[expression.member.name]
                internalAssertion(memberValue != null) {
                    "Member ${expression.member} is accessed but not present."
                }
                memberValue
            }
        }

        fun initialize(resource: Syntax.ResourceExpression, value: Value) {
            val path = resource.asResourcePath()
            if (path != null) {
                currentStackFrame[path] = value
            } else {
                val assertedValue = evaluate(resource)
                ensureReversibility(value == assertedValue) {
                    "Values are not equal"
                }
            }
        }

        fun finalize(resource: Syntax.ResourceExpression): Value {
            val path = resource.asResourcePath()
            return if (path != null) {
                currentStackFrame[path]
            } else {
                evaluate(resource)
            }
        }


        private fun initializeInputs(arguments: List<Value>) {
            val inputs = direction.choose(routine.inputParameters, routine.outputParameters)
                .map { ResourcePath.ofIdentifier(it.name) }
            combineWith(inputs, arguments) { resource, value ->
                currentStackFrame[resource] = value
            }
        }

        private fun finalizeOutputs(): List<Value> {
            val outputs = direction.choose(routine.outputParameters, routine.inputParameters)
                .map { ResourcePath.ofIdentifier(it.name) }
            return outputs.map { resource ->
                currentStackFrame[resource]
            }
        }

        fun ensureReversibility(condition: Boolean, lazyMessage: () -> String) {
            if (!condition) {
                raise(ReversibilityViolation(lazyMessage()))
            }
        }

        fun <E : Throwable> raise(exception: E, origin: Syntax? = null): Nothing {
            TODO("Move into RoutineExecutor for information about currently executed instruction.")
        }
    }

    @OptIn(ExperimentalContracts::class)
    companion object {
        fun internalAssertion(condition: Boolean, lazyMessage: () -> String) {
            contract {
                returns() implies condition
                callsInPlace(lazyMessage, InvocationKind.AT_MOST_ONCE)
            }

            if (!condition)
                throw InternalInconsistencyException(lazyMessage())
        }

        private fun Value.asInteger(): Int {
            internalAssertion(this is Value.Integer) {
                "Operation requires an integer, but a structure is present."
            }
            return this.value
        }

        private fun Value.asStructure(): Map<String, Value> {
            internalAssertion(this is Value.Structure) {
                "Operation requires an integer, but a structure is present."
            }
            return this.members
        }

        operator fun Value.compareTo(other: Value): Int =
            this.asInteger() compareTo other.asInteger()

        fun <T> Boolean.choose(ifTrue: T, ifFalse: T): T =
            if (this) ifTrue else ifFalse

        fun Syntax.ModificationOperator.invert(): Syntax.ModificationOperator = when (this) {
            Syntax.ModificationOperator.ADD -> Syntax.ModificationOperator.SUB
            Syntax.ModificationOperator.SUB -> Syntax.ModificationOperator.ADD
            Syntax.ModificationOperator.XOR -> Syntax.ModificationOperator.XOR
        }
    }
}