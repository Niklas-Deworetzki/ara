package ara.analysis

import ara.Direction
import ara.analysis.dataflow.DataflowProblem
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessDescriptor.*
import ara.control.Block
import ara.storage.ResourceAllocation.variablesCreated
import ara.storage.ResourceAllocation.variablesDestroyed
import ara.storage.ResourcePath
import ara.syntax.Syntax


class LivenessAnalysis(val program: Syntax.Program) {

    private class LivenessProblem(val routine: Syntax.RoutineDefinition) :
        DataflowProblem<Block, LivenessDescriptor>(
            routine.graph.nodes,
            routine.graph::getSuccessors,
            routine.graph::getPredecessors,
            Direction.FORWARD
        ) {

        override fun initialInValue(node: Block): LivenessDescriptor {
            val initialValues = LivenessDescriptor(routine)
            for (inputParameter in routine.inputParameters) {
                initialValues += ResourcePath.ofIdentifier(inputParameter.name) to inputParameter.range
            }
            return initialValues
        }

        override fun initialOutValue(node: Block): LivenessDescriptor =
            LivenessDescriptor(routine)

        private fun combine(a: LivenessState, b: LivenessState): LivenessState = when (a) {
            Unknown ->
                b

            is Initialized -> when (b) {
                is Initialized ->
                    Initialized(a.initializers + b.initializers)

                is Finalized ->
                    Conflict(a.initializers, b.finalizers)

                else ->
                    combine(b, a)
            }

            is Finalized -> when (b) {
                is Finalized ->
                    Finalized(a.finalizers + b.finalizers)

                is Initialized ->
                    Conflict(b.initializers, a.finalizers)

                else ->
                    combine(b, a)
            }

            is Conflict -> when (b) {
                is Conflict ->
                    Conflict(a.initializers + b.initializers, a.finalizers + b.finalizers)

                is Initialized ->
                    Conflict(a.initializers + b.initializers, a.finalizers)

                is Finalized ->
                    Conflict(a.initializers, a.finalizers + b.finalizers)

                else ->
                    combine(b, a)
            }
        }

        override fun combine(a: LivenessDescriptor, b: LivenessDescriptor): LivenessDescriptor {
            val result = LivenessDescriptor(routine)
            for (path in result.keys) {
                val aValue = a[path]
                val bValue = b[path]
                result[path] = combine(aValue, bValue)
            }
            return result
        }

        override fun transfer(value: LivenessDescriptor, node: Block): LivenessDescriptor {
            val liveValues = value.copy()
            for (instruction in node) {
                for (finalized in instruction.variablesDestroyed())
                    liveValues -= finalized to instruction.range
                for (initialized in instruction.variablesCreated())
                    liveValues -= initialized to instruction.range
            }
            return liveValues
        }
    }
}