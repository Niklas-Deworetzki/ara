package ara.analysis.dataflow


interface DataflowSolver<N, L> {

    fun solve(problem: DataflowProblem<N, L>): DataflowSolution<N, L>

    companion object {
        private val defaultSolverInstance = WorklistSolver<Any, Any>()

        @Suppress("UNCHECKED_CAST")
        private fun <N, L> defaultSolver(): DataflowSolver<N, L> {
            return defaultSolverInstance as DataflowSolver<N, L>
        }

        fun <N, L> DataflowProblem<N, L>.solve(solver: DataflowSolver<N, L>): DataflowSolution<N, L> {
            return solver.solve(this)
        }

        fun <N, L> DataflowProblem<N, L>.solve(): DataflowSolution<N, L> {
            return solve(defaultSolver())
        }
    }
}

