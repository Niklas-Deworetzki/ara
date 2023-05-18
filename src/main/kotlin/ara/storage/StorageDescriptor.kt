package ara.storage

import ara.types.Environment
import ara.types.Type
import ara.types.Type.Algebra.Companion.evaluate
import ara.utils.Collections.zipToMap

/**
 * A tree-like lookup-map that provides read and write access to nodes by providing a path.
 * All possible paths must be defined during creation.
 *
 * This abstract implementation provides read and write access for all paths to leaf nodes.
 * Concrete subclasses can choose to provide read and write access for synthetic attributes
 * (i.e. for paths describing non-leaf nodes) as well.
 */
abstract class StorageDescriptor<V>
protected constructor(private val root: DescriptorNode<V>) {

    protected sealed interface DescriptorNode<V> {
        fun copy(): DescriptorNode<V>
    }

    protected class LeafNode<V>(var data: V) : DescriptorNode<V> {
        override fun copy(): DescriptorNode<V> =
            LeafNode(data)
    }

    protected class InnerNode<V>(val data: MutableMap<String, DescriptorNode<V>>) : DescriptorNode<V> {
        override fun copy(): DescriptorNode<V> {
            val copiedData = mutableMapOf<String, DescriptorNode<V>>()
            for ((key, value) in this.data)
                copiedData[key] = value.copy()
            return InnerNode(copiedData)
        }
    }

    private fun findNode(path: ResourcePath): DescriptorNode<V> {
        var currentNode = root
        for (index in path.indices) {
            when (currentNode) {
                is InnerNode ->
                    currentNode = currentNode.data[path[index]]
                        ?: throw NoSuchElementException("No key '${path.subPath(index)}' in descriptor tree.")

                is LeafNode ->
                    throw NoSuchElementException("No key '${path.subPath(index)}' in descriptor tree.")
            }
        }
        return currentNode
    }

    operator fun set(path: ResourcePath, value: V): Unit =
        when (val target = findNode(path)) {
            is LeafNode ->
                target.data = value

            else ->
                setSyntheticValue(target, value)
        }

    operator fun get(path: ResourcePath): V =
        when (val target = findNode(path)) {
            is LeafNode ->
                target.data

            else ->
                getSyntheticValue(target)
        }

    protected abstract fun setSyntheticValue(node: DescriptorNode<V>, value: V)
    protected abstract fun getSyntheticValue(node: DescriptorNode<V>): V

    protected open fun formatValue(value: V): String =
        "$value"

    private fun recursiveToString(node: DescriptorNode<V>): List<String> = when (node) {
        is LeafNode ->
            listOf(formatValue(node.data))

        is InnerNode -> {
            val lineBuffer = mutableListOf<String>()
            for (key in node.data.keys.sorted()) {
                lineBuffer.add(key)

                val formattedValue = recursiveToString(node.data[key]!!)

                val firstLine = formattedValue.first()
                lineBuffer.add("├$firstLine")
                for (remainingLine in formattedValue.drop(1)) {
                    lineBuffer.add("│$remainingLine")
                }
            }
            lineBuffer
        }
    }

    override fun toString(): String =
        recursiveToString(root).joinToString(separator = System.lineSeparator())

    protected companion object {
        fun <V> fromEnvironment(environment: Environment, defaultValue: V): InnerNode<V> {
            val algebra = DescriptorConstructionAlgebra(defaultValue)

            val descriptors = mutableMapOf<String, DescriptorNode<V>>()
            for ((name, type) in environment.variables) {
                descriptors[name.name] = algebra.evaluate(type)
            }
            return InnerNode(descriptors)
        }

        private class DescriptorConstructionAlgebra<V>(val defaultValue: V) : Type.Algebra<DescriptorNode<V>> {
            override fun builtin(builtin: Type.BuiltinType): DescriptorNode<V> =
                LeafNode(defaultValue)

            override fun structure(
                memberNames: List<String>,
                memberValues: List<DescriptorNode<V>>
            ): DescriptorNode<V> =
                InnerNode(zipToMap(memberNames, memberValues))

            override fun uninitializedVariable(): DescriptorNode<V> =
                LeafNode(defaultValue)
        }
    }
}