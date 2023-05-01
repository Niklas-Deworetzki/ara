package ara.position

import kotlin.math.max
import kotlin.math.min

data class Range(val input: InputSource, val offsetStart: Long, val offsetEnd: Long) {

    fun union(range: Range): Range =
        Range(input, min(this.offsetStart, range.offsetStart), max(this.offsetEnd, range.offsetEnd))
}