package ara.control

import ara.Direction
import ara.syntax.Syntax

class Block(val instructions: List<Syntax.Instruction>) : Iterable<Syntax.Instruction> {
    override fun iterator(): Iterator<Syntax.Instruction> = instructions.iterator()
    fun entry(): Syntax.Control = instructions.first() as Syntax.Control
    fun exit(): Syntax.Control = instructions.last() as Syntax.Control
    fun entryLabels(): Collection<Syntax.Identifier> = entry().labels()
    fun exitLabels(): Collection<Syntax.Identifier> = exit().labels()

    companion object {
        fun Syntax.Instruction.isEndOfBlock(): Boolean =
            this is Syntax.Control && this.direction == Direction.FORWARD

        fun Syntax.Instruction.isBeginOfBlock(): Boolean =
            this is Syntax.Control && this.direction == Direction.BACKWARD
    }
}