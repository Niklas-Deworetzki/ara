package ara.control

import ara.Direction
import io.github.rchowell.dotlin.DotNodeShape
import io.github.rchowell.dotlin.digraph
import java.util.*

class ControlGraphVisualizer
private constructor(private val graph: ControlGraph) {

    private val nodeNames: Map<Block, String> = graph.nodes.associateWith {
        "node" + UUID.randomUUID().toString().replace("-", "")
    }

    private val Block.id: String
        get() = nodeNames[this]!!

    private fun format(block: Block): String {
        val inputNames = block.entryLabels().joinToString(separator = ",") { it.name }
            .ifEmpty { graph.routine.name }
        val outputNames = block.exitLabels().joinToString(separator = ",") { it.name }
            .ifEmpty { graph.routine.name }
        return "$inputNames\\n$outputNames"
    }

    override fun toString(): String {
        val graphviz = digraph(graph.routine.name) {
            for (node in graph.nodes) { // Add all nodes.
                +node.id + {
                    label = format(node)
                    shape = DotNodeShape.RECTANGLE
                }
            }

            for (node in graph.nodes) { // Add edges for all nodes.
                for (edge in node.exitLabels()) {
                    val successor = graph.getBlockByLabel(edge, Direction.FORWARD)
                    node.id - successor.id + {
                        label = edge.name
                    }
                }
            }
        }
        return graphviz.dot()
    }

    companion object {
        fun ControlGraph.asGraphString(): String =
            ControlGraphVisualizer(this).toString()
    }
}