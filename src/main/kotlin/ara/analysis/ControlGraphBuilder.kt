package ara.analysis

import ara.control.Block
import ara.control.Block.Companion.isBeginOfBlock
import ara.control.Block.Companion.isEndOfBlock
import ara.control.ControlGraph
import ara.syntax.Syntax
import ara.utils.Collections.sublist

private typealias InstructionBuffer = MutableList<Syntax.Instruction>

class ControlGraphBuilder(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        for (routine in program.declarations.filterIsInstance<Syntax.RoutineDeclaration>()) {
            val blocks = BlockExtractor(routine).extract()
            val graph = GraphConstructor(blocks).construct()

            routine.graph = graph
        }
    }

    inner class BlockExtractor(routine: Syntax.RoutineDeclaration) {
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
                reportError(currentInstruction, "Cannot create a new block before the current one is finished.")
            } else {
                currentBuffer = mutableListOf()
            }
        }

        private fun buildBlock() {
            if (currentBuffer == null) {
                reportError(currentInstruction, "Instruction must be part of a block.")
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

    inner class GraphConstructor(private val blocks: List<Block>) {
        private val successors = mutableMapOf<Syntax.Identifier, Block>()
        private val predecessors = mutableMapOf<Syntax.Identifier, Block>()

        fun construct(): ControlGraph {
            if (blocks.size > 1) {
                indexFwLabels(blocks.last())
                for (block in sublist(blocks, 1, 1)) {
                    indexFwLabels(block)
                    indexBwLabels(block)
                }
                indexFwLabels(blocks.first())
            }
            return ControlGraph(blocks, blocks.first(), blocks.last(), successors, predecessors)
        }

        private fun indexFwLabels(block: Block) {
            for (label in block.entryLabels()) {
                if (successors.containsKey(label)) {
                    reportError(label, "Multiple definitions of $label in an entry point.")
                } else {
                    successors[label] = block
                }
            }
        }

        private fun indexBwLabels(block: Block) {
            for (label in block.exitLabels()) {
                if (predecessors.containsKey(label)) {
                    reportError(label, "Multiple definitions of $label in an exit point.")
                } else {
                    predecessors[label] = block
                }
            }
        }
    }
}