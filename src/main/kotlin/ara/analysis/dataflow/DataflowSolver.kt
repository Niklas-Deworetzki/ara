package ara.analysis.dataflow


interface DataflowSolver<N, L> {

    fun solve(problem: DataflowProblem<N, L>): DataflowSolution<N, L>

    companion object {
        private fun <N, L> defaultSolver(): DataflowSolver<N, L> =
            WorklistSolver()

        fun <N, L> DataflowProblem<N, L>.solve(solver: DataflowSolver<N, L>): DataflowSolution<N, L> =
            solver.solve(this)

        fun <N, L> DataflowProblem<N, L>.solve(): DataflowSolution<N, L> =
            solve(defaultSolver())
    }
}

