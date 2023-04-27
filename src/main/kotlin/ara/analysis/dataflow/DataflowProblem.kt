package ara.analysis.dataflow

import ara.Direction
import java.util.function.Function


abstract class DataflowProblem<N, L>(
    protected val graphNodes: Collection<N>,
    protected val predecessors: Function<N, out Iterable<N>>,
    protected val successors: Function<N, out Iterable<N>>,
    val direction: Direction
) : Collection<N> {

    abstract fun initialInValue(node: N): L

    abstract fun initialOutValue(node: N): L

    abstract fun combine(a: L, b: L): L

    abstract fun transfer(value: L, node: N): L


    override val size: Int
        get() = graphNodes.size

    override fun contains(element: N): Boolean =
        graphNodes.contains(element)

    override fun containsAll(elements: Collection<N>): Boolean =
        graphNodes.containsAll(elements)

    override fun isEmpty(): Boolean =
        graphNodes.isEmpty()

    override fun iterator(): Iterator<N> =
        graphNodes.iterator()


    /**
     * Returns the predecessors of a given node.
     */
    fun getPredecessors(n: N): Iterable<N> {
        return predecessors.apply(n)
    }

    /**
     * Returns the successors of a given node.
     */
    fun getSuccessors(n: N): Iterable<N> {
        return successors.apply(n)
    }
}