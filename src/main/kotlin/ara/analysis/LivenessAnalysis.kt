package ara.analysis

import ara.analysis.dataflow.DataflowSolution
import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessProblem
import ara.control.Block
import ara.syntax.Syntax


class LivenessAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val routines = program.definitions.filterIsInstance<Syntax.RoutineDefinition>()
        for (routine in routines) {
            val dataflowSolution = LivenessProblem(routine).solve()
            for (block in routine.graph) {
                BlockLevelAnalysis(dataflowSolution, block).run()
            }
        }
    }

    private inner class BlockLevelAnalysis(
        liveness: DataflowSolution<Block, LivenessDescriptor>,
        val block: Block
    ) : Runnable {
        val currentState = liveness.getIn(block).copy()

        override fun run() = println(currentState)
    }
}
