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

    private val currentStackFrame: StackFrame
        get() = callStack.first

    private val currentDirection: Direction
        get() = currentStackFrame.direction

    private val currentRoutine: Syntax.RoutineDefinition
        get() = currentStackFrame.routine

    override fun run() {
        val entryPoint = findEntryPoint()

        try {
            val inputValues = entryPoint.inputParameterTypes.map { Value.defaultValueForType(it) }
            val outputValues = executeRoutine(Direction.FORWARD, entryPoint, inputValues)
            combineWith(entryPoint.outputParameters.map { it.name }, outputValues) { parameter, value ->
                println("${parameter.name} = $value")
            }
        } catch (exception: Exception) {
            throw NativeException.withStackTraceFrom(exception, callStack, currentInstruction.range)
        }
    }

    private fun findEntryPoint(): Syntax.RoutineDefinition {
        val entryPoint = program.definitions
            .filterIsInstance<Syntax.RoutineDefinition>()
            .find { it.name == MAIN_ROUTINE_NAME }
        internalAssertion(entryPoint != null) {
            "Program's entry point $MAIN_ROUTINE_NAME is not present."
        }
        return entryPoint
    }

    private lateinit var currentInstruction: Syntax.Instruction
    private lateinit var lastJumpTarget: Syntax.Identifier

    private fun executeRoutine(
        executionDirection: Direction,
        routine: Syntax.RoutineDefinition,
        arguments: List<Value>,
        caller: Syntax.Call? = null
    ): List<Value> =
        withStackFrame(StackFrame(executionDirection, routine, caller)) {
            initializeInputs(arguments)

            val firstBlock = currentDirection.choose(routine.graph.beginBlock, routine.graph.endBlock)
            enqueueForExecution(firstBlock)
            while (currentStackFrame.queuedInstructions.isNotEmpty()) {
                currentInstruction = currentStackFrame.queuedInstructions.remove()
                executeInstruction(currentInstruction)
            }

            finalizeOutputs()
        }

    private inline fun <V> withStackFrame(frame: StackFrame, crossinline action: () -> V): V {
        callStack.push(frame)
        val result = action()
        callStack.pop()
        return result
    }

    private fun initializeInputs(arguments: List<Value>) {
        val inputs = currentDirection.choose(currentRoutine.inputParameters, currentRoutine.outputParameters)
            .map { ResourcePath.ofIdentifier(it.name) }
        combineWith(inputs, arguments) { resource, value ->
            currentStackFrame[resource] = value
        }
    }

    private fun finalizeOutputs(): List<Value> {
        val outputs = currentDirection.choose(currentRoutine.outputParameters, currentRoutine.inputParameters)
            .map { ResourcePath.ofIdentifier(it.name) }
        return outputs.map { resource ->
            currentStackFrame[resource]
        }
    }


    private fun executeInstruction(instruction: Syntax.Instruction) = when (instruction) {
        is Syntax.Assignment -> {
            val finalized = currentDirection.choose(instruction.src, instruction.dst)
            val initialized = currentDirection.choose(instruction.dst, instruction.src)

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

            val finalized = currentDirection.choose(instruction.srcList, instruction.dstList)
            val initialized = currentDirection.choose(instruction.dstList, instruction.srcList)
            val effectiveCallDirection = getEffectiveDirection(instruction.direction)

            val arguments = finalized.map(::finalize)
            val results = executeRoutine(effectiveCallDirection, routine, arguments, instruction)
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
        currentDirection.choose(direction, direction.inverted)

    private fun executeControlFlow(direction: Direction, label: Syntax.Identifier) {
        when (getEffectiveDirection(direction)) {
            Direction.FORWARD -> {
                val targetBlock = currentRoutine.graph.getBlockByLabel(label, direction)
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
        val orderedInstructions = when (currentDirection) {
            Direction.FORWARD -> block
            Direction.BACKWARD -> block.reversed()
        }
        currentStackFrame.queuedInstructions.addAll(orderedInstructions)
    }


    private fun applyModification(value: Int, modifier: Syntax.ArithmeticModifier): Value {
        val modification = evaluateArithmetic(modifier.value)
        val effectiveOperator = currentDirection.choose(
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

    private fun evaluateArithmetic(expression: Syntax.ArithmeticExpression): Int = when (expression) {
        is Syntax.ArithmeticValue ->
            evaluate(expression.value).asInteger()

        is Syntax.ArithmeticBinary -> {
            val lhs = evaluate(expression.lhs).asInteger()
            val rhs = evaluate(expression.rhs).asInteger()

            if (expression.operator in setOf(Syntax.BinaryOperator.DIV, Syntax.BinaryOperator.MOD)) {
                if (rhs == 0) {
                    throw ArithmeticException("Division by 0!")
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

    private fun evaluateCondition(condition: Syntax.ConditionalExpression): Boolean = when (condition) {
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

    private fun evaluate(expression: Syntax.ResourceExpression): Value = when (expression) {
        is Syntax.IntegerLiteral -> Value.Integer(expression.value)
        is Syntax.TypedStorage -> evaluate(expression.storage)
        is Syntax.NamedStorage -> currentStackFrame[expression.asResourcePath()!!]
        is Syntax.MemberAccess -> {
            val structure = evaluate(expression.storage).asStructure()
            val memberValue = structure[expression.member.name]
            internalAssertion(memberValue != null) {
                "Member ${expression.member} is accessed but not present."
            }
            memberValue
        }
    }

    private fun initialize(resource: Syntax.ResourceExpression, value: Value) {
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

    private fun finalize(resource: Syntax.ResourceExpression): Value {
        val path = resource.asResourcePath()
        return if (path != null) {
            currentStackFrame[path]
        } else {
            evaluate(resource)
        }
    }

    companion object {
        val MAIN_ROUTINE_NAME = Syntax.Identifier("main")

        private fun ensureReversibility(condition: Boolean, lazyMessage: () -> String) {
            if (!condition) {
                throw ReversibilityViolation(lazyMessage())
            }
        }

        @OptIn(ExperimentalContracts::class)
        private fun internalAssertion(condition: Boolean, lazyMessage: () -> String) {
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

        private operator fun Value.compareTo(other: Value): Int =
            this.asInteger() compareTo other.asInteger()

        private fun <T> Boolean.choose(ifTrue: T, ifFalse: T): T =
            if (this) ifTrue else ifFalse

        private fun Syntax.ModificationOperator.invert(): Syntax.ModificationOperator = when (this) {
            Syntax.ModificationOperator.ADD -> Syntax.ModificationOperator.SUB
            Syntax.ModificationOperator.SUB -> Syntax.ModificationOperator.ADD
            Syntax.ModificationOperator.XOR -> Syntax.ModificationOperator.XOR
        }
    }
}