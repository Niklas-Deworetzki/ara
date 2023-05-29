package ara.storage

import ara.syntax.Syntax

interface ResourcePath {

    val indices: Iterable<Int>
        get() = TODO()

    operator fun get(index: Int): String = TODO()

    fun subPath(indexIncluded: Int): ResourcePath = TODO()

    fun appended(accessor: String): ResourcePath = TODO()

    companion object {
        fun of(segments: Iterable<String>): ResourcePath = TODO()

        fun ofIdentifier(name: Syntax.Identifier): ResourcePath = TODO()
    }
}