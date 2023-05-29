package ara.analysis

import ara.analysis.dataflow.DataflowSolution
import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessProblem
import ara.analysis.live.LivenessState
import ara.control.Block
import ara.storage.ResourcePath
import ara.syntax.Syntax


class LivenessAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val routines = program.definitions.filterIsInstance<Syntax.RoutineDefinition>()
        for (routine in routines) {
            val dataflowSolution = LivenessProblem(routine).solve()
            for (block in routine.graph) {
                BlockLevelAnalysis(dataflowSolution, block)
            }
        }
    }

    private inner class BlockLevelAnalysis(
        liveness: DataflowSolution<Block, LivenessDescriptor>,
        val block: Block
    ) {
        val currentState = liveness.getIn(block)

    }
}
