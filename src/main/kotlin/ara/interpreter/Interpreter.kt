package ara.interpreter

import ara.Direction
import ara.control.Block
import ara.reporting.Message.Companion.quoted
import ara.storage.MemoryPath
import ara.storage.ResourceAllocation.asMemoryPath
import ara.storage.ResourceAllocation.asResourcePath
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.types.Type
import ara.utils.NonEmptyList
import ara.utils.NonEmptyList.Companion.toNonEmptyList
import ara.utils.combineWith
import java.util.*

class Interpreter(val program: Syntax.Program) : Runnable {
    private val heap: Heap = Heap()
    private val callStack: Deque<StackFrame> = ArrayDeque()

    private val currentStackFrame: StackFrame
        get() = callStack.first

    private val currentDirection: Direction
        get() = currentStackFrame.direction

    private val currentRoutine: Syntax.RoutineDefinition
        get() = currentStackFrame.routine

    override fun run() {
        val entryPoint = findEntryPoint() ?: return

        try {
            val inputValues = entryPoint.signature.inputTypes.map { Value.defaultValueForType(it) }
            val outputValues = executeRoutine(Direction.FORWARD, entryPoint, inputValues)
            combineWith(entryPoint.outputParameters.map { it.name }, outputValues) { parameter, value ->
                println("${parameter.name} = $value")
            }
        } catch (exception: Exception) {
            throw NativeException.withStackTraceFrom(exception, callStack, currentInstruction.range)
        }
    }

    private fun findEntryPoint(): Syntax.RoutineDefinition? =
        program.definitions
            .filterIsInstance<Syntax.RoutineDefinition>()
            .find { it.name == MAIN_ROUTINE_NAME }

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
        is Syntax.ArithmeticAssignment -> {
            val finalized = currentDirection.choose(instruction.src, instruction.dst)
            val initialized = currentDirection.choose(instruction.dst, instruction.src)

            val value = finalize(finalized).asInteger()
            val modifiedValue = applyModification(value, instruction.arithmetic)
            initialize(initialized, modifiedValue)
        }

        is Syntax.MultiAssignment -> {
            val finalized = currentDirection.choose(instruction.srcList, instruction.dstList)
            val initialized = currentDirection.choose(instruction.dstList, instruction.srcList)

            val finalizedValues = finalized.map(::finalize)
            combineWith(initialized, finalizedValues) { resource, value ->
                initialize(resource, value)
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

    private fun evaluate(expression: Syntax.ResourceExpression): Value {
        when (expression) {
            // Evaluate integer literal to value.
            is Syntax.IntegerLiteral ->
                return Value.Integer(expression.value)

            // Component-wise evaluation of members.
            is Syntax.StructureLiteral -> {
                if (expression.computedType == Type.Unit)
                    return Value.Unit

                val evaluatedMembers = expression.members.map { member ->
                    Value.Member(member.name.name, evaluate(member.value))
                }
                return Value.Structure(evaluatedMembers.toNonEmptyList())
            }

            is Syntax.NullReferenceLiteral ->
                return Value.NULL_REFERENCE

            // Fetch resource from path.
            is Syntax.Storage ->
                return currentStackFrame[expression.asResourcePath()]

            // Allocate on heap.
            is Syntax.AllocationExpression -> {
                val allocated = evaluate(expression.value)
                return Value.Reference(heap.allocate(allocated))
            }

            is Syntax.Memory -> {
                val memoryPath = expression.asMemoryPath()
                return getMemory(memoryPath.path, currentStackFrame[memoryPath.resource])
            }
        }
    }

    private fun initialize(resource: Syntax.ResourceExpression, value: Value) {
        when (resource) {
            // Assigning to integer literal verifies assigned value.
            is Syntax.IntegerLiteral ->
                ensureReversibility(resource.value == value.asInteger()) {
                    "Expected ${resource.value} but got ${value.asInteger()}."
                }

            // Component-wise initialization of members.
            is Syntax.StructureLiteral -> {
                if (resource.computedType == Type.Unit)
                    return // Nothing to do.

                val structure = value.asStructure()
                combineWith(resource.members, structure.data) { member, data ->
                    internalAssertion(member.name.name == data.name) {
                        "Member names in structure must be equal."
                    }
                    initialize(member.value, data.value)
                }
            }

            is Syntax.NullReferenceLiteral ->
                ensureReversibility(value.asAddress() == Value.NULL_REFERENCE.address) {
                    "Expected ${Value.NULL_REFERENCE} but got $value"
                }

            // Initialize resource described by path.
            is Syntax.Storage ->
                currentStackFrame[resource.asResourcePath()] = value

            // Unpack reference by free-ing value from heap and initialize contents.
            is Syntax.AllocationExpression -> {
                val released = heap.free(value.asAddress())
                initialize(resource.value, released)
            }

            is Syntax.Memory -> {
                val memoryPath = resource.asMemoryPath()
                updateMemory(memoryPath.path, currentStackFrame[memoryPath.resource], value)
            }
        }
    }

    private fun finalize(resource: Syntax.ResourceExpression): Value =
        evaluate(resource) // finalize is evaluate, since we don't actually clean up values.

    companion object {
        val MAIN_ROUTINE_NAME = Syntax.Identifier("main")

        private fun Value.asInteger(): Int {
            internalAssertion(this is Value.Integer) {
                "Operation requires an integer."
            }
            return this.value
        }

        private fun Value.asStructure(): NonEmptyList<Value.Member> {
            internalAssertion(this is Value.Structure) {
                "Operation requires a structure."
            }
            return this.members
        }

        private fun Value.accessMember(name: String): Value {
            val accessedMember = this.asStructure().find { it.name == name }
            internalAssertion(accessedMember != null) {
                "Operation requires member named ${name.quoted()} on $this."
            }
            return accessedMember.value
        }

        private fun Value.asAddress(): Int {
            internalAssertion(this is Value.Reference) {
                "Operation requires a reference."
            }
            return this.address
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

    private fun getMemory(path: List<MemoryPath.Segment>, value: Value): Value {
        var result = value
        for (segment in path) {
            result = when (segment) {
                MemoryPath.Dereference -> heap[result.asAddress()]
                is MemoryPath.Member -> result.accessMember(segment.name)
            }
        }
        return result
    }

    private fun updateMemory(path: List<MemoryPath.Segment>, accessed: Value, assigned: Value): Value =
        if (path.isEmpty()) assigned
        else when (val segment = path.first()) {
            MemoryPath.Dereference -> {
                val address = accessed.asAddress()
                val updated = updateMemory(path.drop(1), heap[address], assigned)
                heap[address] = updated
                updated
            }

            is MemoryPath.Member -> {
                val updatedMembers = accessed.asStructure().map { member ->
                    if (member.name == segment.name) {
                        val updatedValue = updateMemory(path.drop(1), member.value, assigned)
                        Value.Member(member.name, updatedValue)
                    } else {
                        member
                    }
                }
                Value.Structure(updatedMembers)
            }
        }
}
