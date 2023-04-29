package ara.control

import ara.syntax.Syntax

class ControlGraph(
    val nodes: Collection<Block>,
    val beginBlock: Block,
    val endBlock: Block,
    private val successors: Map<Syntax.Identifier, Block>,
    private val predecessors: Map<Syntax.Identifier, Block>
) : Iterable<Block> {

    override fun iterator(): Iterator<Block> = nodes.iterator()

    fun getPredecessors(block: Block): Set<Block> =
        block.entryLabels().mapNotNull(predecessors::get).toSet()

    fun getSuccessors(block: Block): Set<Block> =
        block.exitLabels().mapNotNull(successors::get).toSet()
}