package ara.storage

import ara.syntax.Syntax

interface ResourcePath {

    val indices: Iterable<Int>
        get() = TODO()

    operator fun get(index: Int): String = TODO()

    fun subPath(indexIncluded: Int): ResourcePath = TODO()

    fun appended(accessor: Syntax.Identifier): ResourcePath = TODO()

    companion object {
        fun localRoot(name: Syntax.Identifier): ResourcePath = TODO()

        fun of(vararg components: String): ResourcePath = TODO()
    }
}