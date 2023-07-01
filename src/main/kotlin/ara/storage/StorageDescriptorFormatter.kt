package ara.storage

import ara.utils.combineWith

internal class StorageDescriptorFormatter<V>(private val descriptor: StorageDescriptor<V>) {
    private fun formatDescriptorEntry(
        key: String,
        value: StorageDescriptor.DescriptorNode<V>
    ): List<String> = when (value) {
        is StorageDescriptor.LeafNode -> {
            val formattedValue = descriptor.formatValue(value.data)
            listOf("$key: $formattedValue")
        }

        is StorageDescriptor.InnerNode -> {
            val buffer = mutableListOf<String>()
            buffer.add(key)
            buffer.addAll(formatInnerNode(value))
            buffer
        }
    }

    private fun formatInnerEntryIntoBuffer(
        buffer: MutableList<String>,
        key: String,
        node: StorageDescriptor.InnerNode<V>,
        firstIndentation: String,
        consecutiveIndentations: String
    ) {
        val rows = formatDescriptorEntry(key, node.data[key]!!)
        val indentation = Indentation(firstIndentation, consecutiveIndentations)
        combineWith(indentation, rows) { indent, row ->
            buffer.add(indent + row)
        }
    }

    private fun formatInnerNode(node: StorageDescriptor.InnerNode<V>): List<String> {
        if (node.data.isEmpty()) return emptyList()

        val buffer = mutableListOf<String>()
        val sortedKeys = node.data.keys.sorted()
        val firstKeys = sortedKeys.dropLast(1)
        for (key in firstKeys) {
            formatInnerEntryIntoBuffer(buffer, key, node, "├", "│")
        }

        val lastKey = sortedKeys.last()
        formatInnerEntryIntoBuffer(buffer, lastKey, node, "└", " ")
        return buffer
    }

    fun format(): String =
        formatInnerNode(descriptor.root).joinToString(separator = System.lineSeparator())

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