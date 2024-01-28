package ara.analysis.live

import ara.analysis.Analysis
import ara.control.Block
import ara.reporting.Message.Companion.quoted
import ara.storage.ResourceAllocation.allMemoryReferences
import ara.storage.ResourceAllocation.asResourcePath
import ara.storage.ResourceAllocation.asResourcePaths
import ara.storage.ResourceAllocation.resources
import ara.storage.ResourceAllocation.resourcesCreated
import ara.storage.ResourceAllocation.resourcesDestroyed
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.syntax.extensions.getDereferencedStorage

class BlockLevelResourceAnalysis(routine: Syntax.RoutineDefinition, val block: Block) : Analysis<Unit>() {
    private val currentState = routine.liveness.getIn(block).copy()

    override fun runAnalysis() {
        for (instruction in block) {
            instruction.resourcesDestroyed().forEach(::finalizeOrReport)
            verifyUses(instruction)
            instruction.resourcesCreated().forEach(::initializeOrReport)
        }
    }

    private fun initializeOrReport(expression: Syntax.ResourceExpression) {
        for (resource in expression.asResourcePaths()) {
            val state = currentState[resource]
            if (state !is LivenessState.Finalized) {
                val error =
                    reportError("Cannot initialize ${resource.quoted()} as it has already been initialized.")
                        .withPositionOf(expression)

                for (initializer in state.initializers) {
                    error.withAdditionalInfo("Potential causes include the initializer defined here:", initializer)
                }

            } else {
                currentState += resource to expression.range
            }
        }
    }

    private fun finalizeOrReport(expression: Syntax.ResourceExpression) {
        for (resource in expression.asResourcePaths()) {
            val state = currentState[resource]
            if (state !is LivenessState.Initialized) {
                val reason = // Message "already been finalized" is misleading if there is no initializer.
                    if (state.initializers.isEmpty()) "it has not been initialized"
                    else "it has already been finalized."
                val error =
                    reportError("Cannot finalize ${resource.quoted()} as $reason.")
                        .withPositionOf(expression)

                for (initializer in state.initializers) {
                    error.withAdditionalInfo("Potential causes include the finalizer defined here:", initializer)
                }

            } else {
                currentState -= resource to expression.range
            }
        }
    }

    private fun verifyUse(resource: ResourcePath, anchor: Syntax) {
        val state = currentState[resource]
        if (state !is LivenessState.Initialized) {
            val error = reportError("Cannot use ${resource.quoted()} as it has not been initialized.")
                .withPositionOf(anchor)

            for (finalizer in state.finalizers) {
                error.withAdditionalInfo("This is potentially caused by the finalizer defined here:", finalizer)
            }
        }
    }

    private fun verifyUses(instruction: Syntax.Instruction) {
        val usedInMemoryReference = instruction.resources().flatMap { it.allMemoryReferences() }
        for (memory in usedInMemoryReference) {
            val storage = memory.getDereferencedStorage()
            verifyUse(storage.asResourcePath(), storage)
        }

        when (instruction) {
            is Syntax.ArithmeticAssignment -> {
                val resources = instruction.arithmetic.value.asResourcePaths().toSet()
                for (resource in resources) {
                    verifyUse(resource, instruction.arithmetic)
                }
            }

            is Syntax.Conditional -> {
                val resources = instruction.condition.asResourcePaths().toSet()
                for (resource in resources) {
                    verifyUse(resource, instruction.condition)
                }
            }

            is Syntax.Call, is Syntax.MultiAssignment, is Syntax.Unconditional ->
                Unit
        }
    }
}