package ara.storage

import ara.types.Environment
import ara.types.Type
import ara.types.Type.Algebra.Companion.evaluate
import ara.utils.Collections.zipToMap
import java.lang.IllegalArgumentException

sealed class StorageDescriptor<V> {
    private class PrimitiveDescriptor<V>(var data: V) :
        StorageDescriptor<V>()

    private class CompoundDescriptor<V>(val data: MutableMap<String, StorageDescriptor<V>>) :
        StorageDescriptor<V>()


    private fun findNode(path: ResourcePath): StorageDescriptor<V> {
        var currentDescriptor = this

        for (index in path.indices) {
            when (currentDescriptor) {
                is CompoundDescriptor ->
                    currentDescriptor = currentDescriptor.data[path[index]]
                        ?: throw NoSuchElementException("No key '${path.subPath(index)}' in tree.")

                is PrimitiveDescriptor ->
                    throw NoSuchElementException("No key '${path.subPath(index)}' in tree.")
            }
        }
        return currentDescriptor
    }

    private fun recursiveSetValue(value: V): Unit = when (this) {
        is PrimitiveDescriptor ->
            this.data = value

        is CompoundDescriptor ->
            this.data.values.forEach {
                it.recursiveSetValue(value)
            }
    }

    operator fun set(vararg path: String, value: V): Unit =
        set(ResourcePath.of(*path), value)

    operator fun set(path: ResourcePath, value: V): Unit =
        findNode(path).recursiveSetValue(value)

    operator fun get(vararg path: String): StorageDescriptor<V> =
        findNode(ResourcePath.of(*path))

    operator fun get(path: ResourcePath): StorageDescriptor<V> =
        findNode(path)

    fun copy(): StorageDescriptor<V> = when (this) {
        is CompoundDescriptor -> {
            val copiedData = mutableMapOf<String, StorageDescriptor<V>>()
            for ((key, value) in this.data)
                copiedData[key] = value.copy()
            CompoundDescriptor(copiedData)
        }

        is PrimitiveDescriptor ->
            PrimitiveDescriptor(this.data)
    }

    fun <R> evaluate(converter: (V) -> R, combinator: (Collection<R>) -> R): R = when (this) {
        is PrimitiveDescriptor ->
            converter(this.data)

        is CompoundDescriptor ->
            combinator(this.data.values.map { it.evaluate(converter, combinator) })
    }

    companion object {
        fun <V> fromEnvironment(environment: Environment, defaultValue: V): StorageDescriptor<V> {
            val algebra = DescriptorConstructionAlgebra(defaultValue)

            val descriptors = mutableMapOf<String, StorageDescriptor<V>>()
            for ((name, type) in environment.variables) {
                descriptors[name.name] = algebra.evaluate(type)
            }
            return CompoundDescriptor(descriptors)
        }

        private class DescriptorConstructionAlgebra<V>(val defaultValue: V) : Type.Algebra<StorageDescriptor<V>> {
            override fun builtin(builtin: Type.BuiltinType): StorageDescriptor<V> =
                PrimitiveDescriptor(defaultValue)

            override fun structure(
                memberNames: List<String>,
                memberValues: List<StorageDescriptor<V>>
            ): StorageDescriptor<V> =
                CompoundDescriptor(zipToMap(memberNames, memberValues))

            override fun uninitializedVariable(): StorageDescriptor<V> =
                PrimitiveDescriptor(defaultValue)
        }


        fun <L, R, V> combine(
            l: StorageDescriptor<L>,
            r: StorageDescriptor<R>,
            combinator: (L, R) -> V
        ): StorageDescriptor<V> = when {
            l is CompoundDescriptor && r is CompoundDescriptor -> {
                val resultData = mutableMapOf<String, StorageDescriptor<V>>()
                for (key in (l.data.keys intersect r.data.keys)) {
                    resultData[key] = combine(l.data[key]!!, r.data[key]!!, combinator)
                }
                CompoundDescriptor(resultData)
            }

            l is PrimitiveDescriptor && r is PrimitiveDescriptor ->
                PrimitiveDescriptor(combinator(l.data, r.data))

            else ->
                throw IllegalArgumentException("Different tree structure.")
        }
    }
}