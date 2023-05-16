package ara.analysis

import ara.Direction
import ara.analysis.dataflow.DataflowProblem
import ara.control.Block
import ara.storage.ResourceAllocation.variablesCreated
import ara.storage.ResourceAllocation.variablesDestroyed
import ara.storage.StorageDescriptor
import ara.syntax.Syntax


class LivenessAnalysis(val program: Syntax.Program) {


    private class LivenessProblem(val routine: Syntax.RoutineDefinition, direction: Direction) :
        DataflowProblem<Block, StorageDescriptor<Boolean>>(
            routine.graph.nodes,
            routine.graph::getSuccessors,
            routine.graph::getPredecessors,
            direction
        ) {

        override fun initialInValue(node: Block): StorageDescriptor<Boolean> {
            val storage = StorageDescriptor.fromEnvironment(routine.localEnvironment, false)
            if (direction == Direction.FORWARD) {
                for (inputParameter in routine.inputParameters)
                    storage[inputParameter.name.name] = true
            }
            return storage
        }

        override fun initialOutValue(node: Block): StorageDescriptor<Boolean> {
            val storage = StorageDescriptor.fromEnvironment(routine.localEnvironment, false)
            if (direction == Direction.BACKWARD) {
                for (outputParameter in routine.outputParameters)
                    storage[outputParameter.name.name] = true
            }
            return storage
        }

        override fun combine(a: StorageDescriptor<Boolean>, b: StorageDescriptor<Boolean>): StorageDescriptor<Boolean> =
            StorageDescriptor.combine(a, b, Boolean::or)

        override fun transfer(value: StorageDescriptor<Boolean>, node: Block): StorageDescriptor<Boolean> {
            val result = value.copy()
            for (instruction in node) {
                for (destroyed in instruction.variablesDestroyed()) {
                    result[destroyed] = false
                }
                for (created in instruction.variablesCreated()) {
                    result[created] = true
                }
            }
            return result
        }
    }
}