package ara.position

import kotlin.math.max
import kotlin.math.min

data class Range(val input: InputSource, val offsetStart: Long, val offsetEnd: Long) : Comparable<Range> {

    infix fun union(range: Range): Range =
        Range(input, min(this.offsetStart, range.offsetStart), max(this.offsetEnd, range.offsetEnd))

    override fun compareTo(other: Range): Int {
        if (this.input.filename() == other.input.filename())
            return this.offsetStart.compareTo(other.offsetStart)

        return Comparator.nullsFirst(Comparator.naturalOrder<String>()).compare(
            this.input.filename(),
            other.input.filename()
        )
    }

}