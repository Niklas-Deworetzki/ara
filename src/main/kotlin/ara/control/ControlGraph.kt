package ara.control

import ara.Direction
import ara.syntax.Syntax

class ControlGraph(
    val routine: Syntax.Identifier,
    val nodes: Collection<Block>,
    val beginBlock: Block,
    val endBlock: Block,
    private val successors: Map<Syntax.Identifier, Block>,
    private val predecessors: Map<Syntax.Identifier, Block>
) : Collection<Block> {

    override fun iterator(): Iterator<Block> = nodes.iterator()

    override val size: Int
        get() = nodes.size

    override fun contains(element: Block): Boolean =
        element in nodes

    override fun containsAll(elements: Collection<Block>): Boolean =
        nodes.containsAll(elements)

    override fun isEmpty(): Boolean =
        nodes.isEmpty()


    fun getPredecessors(block: Block): Set<Block> =
        block.entryLabels().mapNotNull(predecessors::get).toSet()

    fun getSuccessors(block: Block): Set<Block> =
        block.exitLabels().mapNotNull(successors::get).toSet()

    fun getBlockByLabel(name: Syntax.Identifier, direction: Direction): Block {
        return direction.choose(successors, predecessors)[name]
            ?: throw NoSuchElementException("No label named $name in control graph.")
    }
}