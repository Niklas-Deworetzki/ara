package ara.utils.formatting

fun <E> List<E>.formatToHumanReadable(
    normalSeparator: String,
    lastSeparator: String,
    toStringFunction: (E) -> String = java.lang.String::valueOf
): String? = when (this.size) {
    0 -> null
    1 -> toStringFunction(this.first())
    else -> {
        val separatedList = this.dropLast(1)
            .joinToString(normalSeparator, transform = toStringFunction)
        val lastElement = toStringFunction(this.last())
        separatedList + lastSeparator + lastElement
    }
}
