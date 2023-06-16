package ara.control

import ara.Direction
import ara.syntax.Syntax

class Block(private val instructions: List<Syntax.Instruction>) : Iterable<Syntax.Instruction> {
    override fun iterator(): Iterator<Syntax.Instruction> = instructions.iterator()

    fun entryLabels(): Collection<Syntax.Identifier> =
        when (val entry = instructions.firstOrNull()) {
            is Syntax.Control -> entry.labels()
            else -> emptySet()
        }

    fun exitLabels(): Collection<Syntax.Identifier> =
        when (val exit = instructions.lastOrNull()) {
            is Syntax.Control -> exit.labels()
            else -> emptySet()
        }

    companion object {
        fun Syntax.Instruction.isEndOfBlock(): Boolean =
            this is Syntax.Control && this.direction == Direction.FORWARD

        fun Syntax.Instruction.isBeginOfBlock(): Boolean =
            this is Syntax.Control && this.direction == Direction.BACKWARD
    }
}