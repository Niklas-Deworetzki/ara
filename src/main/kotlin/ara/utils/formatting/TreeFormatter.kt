package ara.utils.formatting

import ara.utils.combineWith

abstract class TreeFormatter<N, V> {

    fun formatTree(root: N): String =
        if (isLeafNode(root))
            formatValue(extractValue(root))
        else
            formatChildren(extractChildren(root))
                .joinToString(separator = System.lineSeparator())

    protected open fun formatValue(value: V): String =
        "$value"

    protected abstract fun isLeafNode(node: N): Boolean

    protected abstract fun extractValue(node: N): V

    protected abstract fun extractChildren(node: N): Map<String, N>


    private fun formatNode(key: String, node: N): List<String> =
        if (isLeafNode(node)) {
            val formattedValue = formatValue(extractValue(node))
            listOf("$key: $formattedValue")
        } else {
            mutableListOf<String>().apply {
                add(key)
                addAll(formatChildren(extractChildren(node)))
            }
        }

    private fun formatInnerNodeIntoBuffer(
        buffer: MutableList<String>,
        key: String,
        children: Map<String, N>,
        firstIndentation: String,
        consecutiveIndentations: String
    ) {
        val rows = formatNode(key, children[key]!!)
        val indentation = Indentation(firstIndentation, consecutiveIndentations)
        combineWith(indentation, rows) { indent, row ->
            buffer.add(indent + row)
        }
    }

    private fun formatChildren(children: Map<String, N>): List<String> {
        if (children.isEmpty()) return emptyList()
        return mutableListOf<String>().apply {
            val sortedKeys = children.keys.sorted()
            val firstKeys = sortedKeys.dropLast(1)
            for (key in firstKeys) {
                formatInnerNodeIntoBuffer(this, key, children, "├", "│")
            }

            val lastKey = sortedKeys.last()
            formatInnerNodeIntoBuffer(this, lastKey, children, "└", " ")
        }
    }

    private class Indentation(val first: String, val consecutive: String) : Iterable<String> {
        override fun iterator(): Iterator<String> = object : Iterator<String> {
            override fun hasNext(): Boolean = true

            private var isFirst = true
            override fun next(): String = when {
                isFirst -> {
                    isFirst = false
                    first
                }

                else -> consecutive
            }
        }
    }
}