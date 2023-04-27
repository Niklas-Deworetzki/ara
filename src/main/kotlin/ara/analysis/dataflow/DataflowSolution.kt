package ara.analysis.dataflow


data class DataflowSolution<N, L>(val inValues: Map<N, L>, val outValues: Map<N, L>) {
    init {
        assert(inValues.keys == outValues.keys) { "IN and OUT must be defined for the same keys." }
    }

    fun getIn(node: N): L = inValues[node]!!

    fun getOut(node: N): L = outValues[node]!!
}

