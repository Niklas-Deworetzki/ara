package ara.storage

import ara.syntax.Syntax

class ResourcePath
private constructor(private val path: List<String>) {

    val indices: Iterable<Int>
        get() = path.indices

    operator fun get(index: Int): String =
        path[index]

    fun subPath(indexIncluded: Int): ResourcePath =
        ResourcePath(path.subList(0, indexIncluded))

    fun appended(accessor: Syntax.Identifier): ResourcePath =
        ResourcePath(path + accessor.name)

    companion object {
        fun of(segments: Iterable<String>): ResourcePath =
            ResourcePath(segments.toList())

        fun ofIdentifier(name: Syntax.Identifier): ResourcePath =
            ResourcePath(listOf(name.name))
    }
}