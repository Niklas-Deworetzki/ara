package ara.analysis

import ara.analysis.memory.MarkableMemoryDescriptor
import ara.reporting.Message
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

        val createdAndNotDestroyed = createdMemory - destroyedMemory
        val destroyedAndNotCreated = destroyedMemory - createdMemory
        reportMismatch(createdAndNotDestroyed, destroyedAndNotCreated, instruction)
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

    private fun reportMismatch(
        createdAndNotDestroyed: Collection<MemoryPath>,
        destroyedAndNotCreated: Collection<MemoryPath>,
        instruction: Syntax.Instruction
    ) {
        if (createdAndNotDestroyed.isNotEmpty() || destroyedAndNotCreated.isNotEmpty()) {
            val message = reportError("Memory resources must be destroyed and created in the same assignment.")
                .withPositionOf(instruction)
            addResourcesList(message, createdAndNotDestroyed, "Resources created but not destroyed:")
            addResourcesList(message, destroyedAndNotCreated, "Resources destroyed but not created:")
        }
    }

    private fun addResourcesList(message: Message, resources: Collection<MemoryPath>, header: String) {
        if (resources.isNotEmpty()) {
            message.withAdditionalInfo(header)
            for (memoryPath in resources) {
                message.withAdditionalInfo("- $memoryPath")
            }
        }
    }
}
