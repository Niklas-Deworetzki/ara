package ara.analysis

import ara.Direction
import ara.analysis.dataflow.DataflowProblem
import ara.control.Block
import ara.storage.StorageDescriptor
import ara.syntax.Syntax
import java.util.*

class LivenessAnalysis(val program: Syntax.Program) {


    companion object {
        private fun Syntax.ResourceExpression.createdResource(): LinkedList<String>? = when (this) {
            is Syntax.MemberAccess -> {
                val result = this.storage.createdResource()
                result?.addFirst(this.member.name)
                result
            }

            is Syntax.NamedStorage -> {
                val result = LinkedList<String>()
                result.addFirst(this.name.name)
                result
            }

            is Syntax.TypedStorage ->
                this.storage.createdResource()

            is Syntax.IntegerLiteral ->
                null
        }

        private fun variablesCreated(instruction: Syntax.Instruction): Set<List<String>> = when (instruction) {
            is Syntax.Assignment ->
                setOfNotNull(instruction.dst.createdResource())

            is Syntax.Call ->
                instruction.dstList.mapNotNull { it.createdResource() }.toSet()

            else ->
                emptySet()
        }

        private fun variablesDestroyed(instruction: Syntax.Instruction): Set<List<String>> = when (instruction) {
            is Syntax.Assignment ->
                setOfNotNull(instruction.src.createdResource())

            is Syntax.Call ->
                instruction.srcList.mapNotNull { it.createdResource() }.toSet()

            else ->
                emptySet()
        }
    }

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
                for (destroyed in variablesDestroyed(instruction)) {
                    result[destroyed] = false
                }
                for (created in variablesCreated(instruction)) {
                    result[created] = true
                }
            }
            return result
        }
    }
}