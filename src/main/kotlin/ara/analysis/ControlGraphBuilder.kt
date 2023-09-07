package ara.analysis

import ara.control.Block
import ara.control.Block.Companion.isBeginOfBlock
import ara.control.Block.Companion.isEndOfBlock
import ara.control.ControlGraph
import ara.control.ControlGraphVisualizer.Companion.asGraphString
import ara.syntax.Syntax
import ara.syntax.extensions.routines
import ara.utils.sublist

private typealias InstructionBuffer = MutableList<Syntax.Instruction>

/**
 * Analysis pass building the control graph for every defined routine.
 *
 * Control graphs are constructed in two steps:
 *  1. Routines a split into [Block]s of instructions between (and including) entry and exit points.
 *  2. Basic blocks within a routine are connected via their labels.
 */
class ControlGraphBuilder(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        for (routine in program.routines) {
            val blocks = BlockExtractor(routine).extract()
            val graph = GraphConstructor(routine, blocks).construct()

            routine.graph = graph
            debug {
                routine.graph.asGraphString()
            }
        }
    }

    inner class BlockExtractor(routine: Syntax.RoutineDefinition) {
        private val iterator = routine.body.iterator()
        private val detectedBlocks = mutableListOf<InstructionBuffer>()

        private var currentBuffer: InstructionBuffer? = null
        private lateinit var currentInstruction: Syntax.Instruction

        fun extract(): List<Block> {
            startBlock()
            while (iterator.hasNext()) {
                currentInstruction = iterator.next()

                if (currentInstruction.isBeginOfBlock()) {
                    startBlock()
                }
                buildBlock()
                if (currentInstruction.isEndOfBlock()) {
                    finishBlock()
                }
            }
            finishBlock()

            return detectedBlocks.map(::Block)
        }

        private fun startBlock() {
            if (currentBuffer != null) {
                reportError("Entry points are only allowed to appear after an exit point.")
                    .withPositionOf(currentInstruction)
            } else {
                currentBuffer = mutableListOf()
            }
        }

        private fun buildBlock() {
            if (currentBuffer == null) {
                reportError("Instructions are only allowed to appear within a block.")
                    .withPositionOf(currentInstruction)
            } else {
                currentBuffer!!.add(currentInstruction)
            }
        }

        private fun finishBlock() {
            if (currentBuffer != null) {
                detectedBlocks.add(currentBuffer!!)
                currentBuffer = null
            }
        }
    }

    inner class GraphConstructor(private val routine: Syntax.RoutineDefinition, private val blocks: List<Block>) {
        private val successors = mutableMapOf<Syntax.Identifier, Block>()
        private val predecessors = mutableMapOf<Syntax.Identifier, Block>()

        fun construct(): ControlGraph {
            if (blocks.size > 1) { // Otherwise there are no labels to index.
                indexBwLabels(blocks.first())
                for (block in sublist(blocks, 1, 1)) {
                    indexFwLabels(block)
                    indexBwLabels(block)
                }
                indexFwLabels(blocks.last())
            }
            reportUnmatchedLabels()
            return ControlGraph(routine.name, blocks, blocks.first(), blocks.last(), successors, predecessors)
        }

        private fun indexFwLabels(block: Block) {
            for (label in block.entryLabels()) {
                if (successors.containsKey(label)) {
                    reportError("Multiple definitions of $label in an entry point.")
                        .withPositionOf(label)
                } else {
                    successors[label] = block
                }
            }
        }

        private fun indexBwLabels(block: Block) {
            for (label in block.exitLabels()) {
                if (predecessors.containsKey(label)) {
                    reportError("Multiple definitions of $label in an exit point.")
                        .withPositionOf(label)
                } else {
                    predecessors[label] = block
                }
            }
        }

        private fun reportUnmatchedLabels() {
            val fwLabels = successors.keys
            val bwLabels = predecessors.keys

            (fwLabels - bwLabels).forEach {
                reportError("Label $it has no associated exit point.")
                    .withPositionOf(it)
            }
            (bwLabels - fwLabels).forEach {
                reportError("Label $it has no associated entry point.")
                    .withPositionOf(it)
            }
        }
    }
}