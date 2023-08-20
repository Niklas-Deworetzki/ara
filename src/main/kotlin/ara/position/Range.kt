package ara.position

data class Range(val input: InputSource, val offsetStart: Long, val offsetEnd: Long) : Comparable<Range> {

    override fun compareTo(other: Range): Int {
        if (this.input.filename() == other.input.filename())
            return this.offsetStart.compareTo(other.offsetStart)

        return Comparator.nullsFirst(Comparator.naturalOrder<String>()).compare(
            this.input.filename(),
            other.input.filename()
        )
    }
}