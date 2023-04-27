package ara.analysis.dataflow

import ara.Direction
import java.util.*

class WorklistSolver<N, L> : DataflowSolver<N, L> {

    private fun equationInputNodesFor(problem: DataflowProblem<N, L>, node: N): Iterable<N> =
        when (problem.direction) {
            Direction.FORWARD -> problem.getPredecessors(node)
            Direction.BACKWARD -> problem.getSuccessors(node)
        }

    private fun equationOutputNodesFor(problem: DataflowProblem<N, L>, node: N): Iterable<N> =
        when (problem.direction) {
            Direction.FORWARD -> problem.getSuccessors(node)
            Direction.BACKWARD -> problem.getPredecessors(node)
        }

    override fun solve(problem: DataflowProblem<N, L>): DataflowSolution<N, L> {
        val equationInValues = mutableMapOf<N, L>()
        val equationOutValues = mutableMapOf<N, L>()

        when (problem.direction) {
            Direction.FORWARD ->
                for (node in problem) {
                    equationInValues[node] = problem.initialInValue(node)
                    equationOutValues[node] = problem.initialOutValue(node)
                }

            Direction.BACKWARD ->
                for (node in problem) {
                    equationInValues[node] = problem.initialOutValue(node)
                    equationOutValues[node] = problem.initialInValue(node)
                }
        }

        val worklist = LinkedList(problem)
        while (worklist.isNotEmpty()) {
            val node = worklist.removeFirst()

            val allEquationInputs = equationInputNodesFor(problem, node)
                .mapNotNull(equationInValues::get)
            val combinedEquationInput = when (allEquationInputs.size) {
                2 -> problem.combine(allEquationInputs[0], allEquationInputs[1])
                1 -> allEquationInputs.first()
                else -> equationInValues[node]!!
            }

            val equationOutput = problem.transfer(combinedEquationInput, node)
            if (equationOutput != equationOutValues.put(node, equationOutput)) {
                for (affectedByOutput in equationOutputNodesFor(problem, node)) {
                    worklist.add(affectedByOutput)
                }
            }
        }

        return when (problem.direction) {
            Direction.FORWARD -> DataflowSolution(equationInValues, equationOutValues)
            Direction.BACKWARD -> DataflowSolution(equationOutValues, equationInValues)
        }
    }
}
