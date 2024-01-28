package ara.storage

import ara.reporting.Message.Companion.quoted
import ara.types.Environment
import ara.types.Type
import ara.utils.NonEmptyList
import ara.utils.get
import ara.utils.zip
import java.util.*

/**
 * A tree-like lookup-map that provides read and write access to nodes by providing a path.
 * All possible paths must be defined during creation.
 *
 * This abstract implementation provides read and write access for all paths to leaf nodes.
 * Concrete subclasses can choose to provide read and write access for synthetic attributes
 * (i.e. for paths describing non-leaf nodes) as well.
 */
abstract class StorageDescriptor<V>
protected constructor(val root: InnerNode<V>) {

    sealed interface DescriptorNode<V> {
        fun copy(): DescriptorNode<V>
    }

    class LeafNode<V>(var data: V) : DescriptorNode<V> {
        override fun copy(): LeafNode<V> =
            LeafNode(data)

        override fun equals(other: Any?): Boolean =
            other is LeafNode<*> && other.data == data

        override fun hashCode(): Int =
            data.hashCode()
    }

    data class Entry<V>(override val key: String, override val value: DescriptorNode<V>) :
        Map.Entry<String, DescriptorNode<V>>

    class InnerNode<V>(val data: List<Entry<V>>) :
        DescriptorNode<V>, Iterable<DescriptorNode<V>> {

        override fun iterator(): Iterator<DescriptorNode<V>> =
            this.data.map { it.value }.iterator()

        override fun copy(): InnerNode<V> {
            val copiedData = this.data.map { (key, value) ->
                Entry(key, value.copy())
            }
            return InnerNode(copiedData)
        }

        override fun equals(other: Any?): Boolean =
            other is InnerNode<*> && other.data == data

        override fun hashCode(): Int =
            data.hashCode()

        operator fun get(key: String): DescriptorNode<V>? =
            data[key]
    }


    val keys: Set<ResourcePath> =
        collectKeys(root).map(ResourcePath::of).toSet()

    /**
     * Translate [ResourcePath] to [DescriptorNode] in this [StorageDescriptor].
     */
    protected fun findNode(path: ResourcePath): DescriptorNode<V> {
        var currentNode: DescriptorNode<V> = root
        for (index in path.indices) {
            when (currentNode) {
                is InnerNode ->
                    currentNode = currentNode[path[index]]
                        ?: throw NoSuchElementException("No key ${path.subPath(index).quoted()} in descriptor tree.")

                is LeafNode ->
                    throw NoSuchElementException("No key ${path.subPath(index).quoted()} in descriptor tree.")
            }
        }
        return currentNode
    }

    override fun equals(other: Any?): Boolean {
        return other is StorageDescriptor<*> && this.root == other.root
    }

    override fun hashCode(): Int {
        return root.hashCode()
    }

    abstract class WithGetSet<N, S>(root: InnerNode<N>) : StorageDescriptor<N>(root) {
        operator fun set(path: ResourcePath, value: S): Unit =
            setNodeValue(findNode(path), value)

        operator fun get(path: ResourcePath): S =
            getNodeValue(findNode(path))

        protected abstract fun setNodeValue(node: DescriptorNode<N>, value: S)
        protected abstract fun getNodeValue(node: DescriptorNode<N>): S
    }


    /**
     * Controls how values in this [StorageDescriptor] are represented
     * when creating a [String] representation.
     */
    internal open fun formatValue(value: V): String =
        "$value"

    override fun toString(): String =
        StorageDescriptorFormatter(this).format()

    protected companion object {
        private fun <V> collectKeys(node: InnerNode<V>): List<Deque<String>> {
            val result = mutableListOf<Deque<String>>()
            for ((key, subNode) in node.data) {
                when (subNode) {
                    is LeafNode -> {
                        val path = LinkedList<String>()
                        path.push(key)
                        result.add(path)
                    }

                    is InnerNode -> {
                        val children = collectKeys(subNode)
                        for (path in children) {
                            path.push(key)
                            result.add(path)
                        }
                    }
                }
            }
            return result
        }

        fun <V> fromEnvironment(environment: Environment, defaultValue: V): InnerNode<V> =
            fromEnvironment(environment, DefaultValueDescriptorConstructionAlgebra(defaultValue))

        @JvmStatic
        fun <V> fromEnvironment(
            environment: Environment,
            constructor: (Type) -> DescriptorNode<V>
        ): InnerNode<V> {
            val orderedVariables = environment.variables.sortedBy { it.key }
            val descriptors = orderedVariables.map { (variable, type) ->
                Entry(variable.name, constructor(type))
            }
            return InnerNode(descriptors.toList())
        }
    }

    /**
     * Algebra used to initialize [StorageDescriptor] with default value in all nodes.
     */
    private class DefaultValueDescriptorConstructionAlgebra<V>(val defaultValue: V) :
        DescriptorConstructionAlgebra<V>() {

        override fun createForMaterializedType(type: Type.MaterializedType): V =
            defaultValue

        override fun createForUninitializedType(): V =
            defaultValue
    }

    abstract class DescriptorConstructionAlgebra<V> :
        Type.Algebra<DescriptorNode<V>> {

        abstract fun createForMaterializedType(type: Type.MaterializedType): V

        abstract fun createForUninitializedType(): V

        override fun uninitializedVariable(): DescriptorNode<V> =
            LeafNode(createForUninitializedType())

        override fun builtin(builtin: Type.Builtin): DescriptorNode<V> =
            LeafNode(createForMaterializedType(builtin))

        override fun reference(base: Type): DescriptorNode<V> =
            LeafNode(createForMaterializedType(Type.Reference(base)))

        override fun structure(
            memberNames: NonEmptyList<String>,
            memberValues: NonEmptyList<DescriptorNode<V>>
        ): DescriptorNode<V> =
            InnerNode(zip(memberNames, memberValues, ::Entry))
    }
}