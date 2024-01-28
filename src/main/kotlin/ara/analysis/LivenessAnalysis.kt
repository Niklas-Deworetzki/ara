package ara.analysis

import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.BlockLevelResourceAnalysis
import ara.analysis.live.ConflictAnalysis
import ara.analysis.live.LivenessProblem
import ara.analysis.live.RoutineLevelResourceAnalysis
import ara.syntax.Syntax


class LivenessAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() = forEachRoutineIn(program) {
        routine.liveness = LivenessProblem(routine).solve()

        andThen { ConflictAnalysis(routine) }
        andThen { RoutineLevelResourceAnalysis(routine) }
        proceedAnalysis {
            for (block in routine.graph) {
                includeAnalysis(
                    BlockLevelResourceAnalysis(routine, block)
                )
            }
        }
    }
}
