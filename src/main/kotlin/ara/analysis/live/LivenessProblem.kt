package ara.analysis.live

import ara.Direction
import ara.analysis.dataflow.DataflowProblem
import ara.analysis.live.LivenessState.Companion.meet
import ara.control.Block
import ara.storage.ResourceAllocation.variablesCreated
import ara.storage.ResourceAllocation.variablesDestroyed
import ara.syntax.Syntax

class LivenessProblem(val routine: Syntax.RoutineDefinition, direction: Direction = Direction.FORWARD) :
    DataflowProblem<Block, LivenessDescriptor>(
        routine.graph.nodes,
        routine.graph::getPredecessors,
        routine.graph::getSuccessors,
        direction
    ) {

    override fun initialInValue(node: Block): LivenessDescriptor =
        if (direction == Direction.FORWARD && node == routine.graph.beginBlock)
            LivenessDescriptor.fromParameterList(routine, Direction.FORWARD)
        else
            LivenessDescriptor(routine, LivenessState.Unknown)

    override fun initialOutValue(node: Block): LivenessDescriptor =
        if (direction == Direction.BACKWARD && node == routine.graph.endBlock)
            LivenessDescriptor.fromParameterList(routine, Direction.BACKWARD)
        else
            LivenessDescriptor(routine, LivenessState.Unknown)

    private fun combine(a: LivenessState, b: LivenessState): LivenessState =
        a meet b

    override fun combine(a: LivenessDescriptor, b: LivenessDescriptor): LivenessDescriptor {
        val result = LivenessDescriptor(routine, LivenessState.Unknown)
        for (path in result.keys) {
            result[path] = combine(a[path], b[path])
        }
        return result
    }

    override fun transfer(value: LivenessDescriptor, node: Block): LivenessDescriptor {
        val liveValues = value.copy()
        for (instruction in node) {
            for (finalized in instruction.variablesDestroyed())
                liveValues -= finalized to instruction.range
            for (initialized in instruction.variablesCreated())
                liveValues += initialized to instruction.range
        }
        return liveValues
    }
}
