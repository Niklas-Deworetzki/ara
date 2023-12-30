package ara.analysis

import ara.analysis.memory.MarkableMemoryDescriptor
import ara.reporting.Message.Companion.quoted
import ara.storage.MemoryPath
import ara.storage.ResourceAllocation.asMemoryPaths
import ara.storage.ResourceAllocation.resourcesCreated
import ara.storage.ResourceAllocation.resourcesDestroyed
import ara.syntax.Syntax
import ara.types.Environment

class MemoryResourceAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() = forEachRoutineIn(program) {
        for (instruction in routine.body) {
            verifyInstructionKeepsMemoryValid(routine.localEnvironment, instruction)
        }
    }

    private fun verifyInstructionKeepsMemoryValid(environment: Environment, instruction: Syntax.Instruction) {
        val destroyedMemory = markMemoryAndReportWhenMarkedTwice(environment, instruction.resourcesDestroyed()) {
            "Memory resource ${it.quoted()} has already been destroyed by this assignment."
        }
        val createdMemory = markMemoryAndReportWhenMarkedTwice(environment, instruction.resourcesCreated()) {
            "Memory resource ${it.quoted()} has already been created by this assignment."
        }

        val destroyedAndNotCreated = destroyedMemory - createdMemory
        val createdAndNotDestroyed = createdMemory - destroyedMemory
        reportIfNotEmpty(
            destroyedAndNotCreated,
            instruction,
            "Memory resources must be created after they are destroyed.",
            "The following resources were not created:"
        )
        reportIfNotEmpty(
            createdAndNotDestroyed,
            instruction,
            "Memory resources must be destroyed before they can be created.",
            "The following resources were not destroyed:"
        )
    }

    private fun markMemoryAndReportWhenMarkedTwice(
        environment: Environment,
        resources: Collection<Syntax.ResourceExpression>,
        whenMarkedTwice: (MemoryPath) -> String
    ): MarkableMemoryDescriptor {
        val memoryDescriptor = MarkableMemoryDescriptor(environment)
        for (resource in resources) {
            for (path in resource.asMemoryPaths()) {
                val hasBeenMarkedBefore = memoryDescriptor.mark(path)
                if (hasBeenMarkedBefore) {
                    val text = whenMarkedTwice(path)
                    reportError(text)
                        .withPositionOf(resource)
                }
            }
        }
        return memoryDescriptor
    }

    private fun reportIfNotEmpty(
        resources: Collection<MemoryPath>,
        instruction: Syntax.Instruction,
        errorHeading: String,
        resourceListHeading: String
    ) {
        if (resources.isNotEmpty()) {
            val message = reportError(errorHeading)
                .withPositionOf(instruction)
                .withAdditionalInfo(resourceListHeading)
            for (resource in resources) {
                message.withAdditionalInfo("- $resource")
            }
        }
    }
}
