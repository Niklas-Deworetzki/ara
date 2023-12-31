package ara.analysis.memory

import ara.storage.MemoryPath
import ara.storage.ResourcePath
import ara.types.Type
import ara.utils.formatting.formatToHumanReadable

sealed interface MarkableMemory {

    var isMarked: Boolean

    fun access(segment: MemoryPath.Segment): MarkableMemory

    class Structure(type: Type.Structure) : MarkableMemory {
        private val members = type.members.associate {
            it.name to unmarkedForType(it.type)
        }

        override var isMarked: Boolean
            get() = members.values.any { it.isMarked }
            set(value) {
                for (member in this.members.values) {
                    member.isMarked = value
                }
            }

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            require(segment is MemoryPath.Member) { "Structure can only be accessed via member access operation." }
            require(segment.name in members.keys) {
                val validMemberNames = members.keys.formatToHumanReadable(", ", ", or ")
                "Member name must be one of $validMemberNames."
            }
            return members[segment.name]!!
        }

        internal fun subtractMarked(current: MemoryPath, other: Structure): Collection<MemoryPath> {
            if (!other.isMarked && this.members.values.all { it.isMarked }) {
                return setOf(current) // Return root of struct as difference instead of all members.
            }
            return members.flatMap { (key, thisValue) ->
                val otherValue = other.members[key]!!
                subtract(
                    current.withAccessedMember(key),
                    thisValue,
                    otherValue
                )
            }
        }
    }

    class Reference(private val type: Type.Reference) : MarkableMemory {
        private var referenced: MarkableMemory? = null
        private var isSelfMarked: Boolean = false

        override var isMarked: Boolean
            get() = referenced?.isMarked ?: isSelfMarked
            set(value) {
                isSelfMarked = value
            }

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            require(segment is MemoryPath.Dereference) { "Reference can only be accessed via dereference operation." }
            if (referenced == null) {
                referenced = unmarkedForType(type.base)
            }
            return referenced!!
        }

        internal fun subtractMarked(current: MemoryPath, other: Reference): Collection<MemoryPath> {
            if (this.isSelfMarked && !other.isSelfMarked)
                return setOf(current)

            if (!this.isSelfMarked && !other.isSelfMarked) {
                // Both have marked references, compare them.
                return subtract(
                    current.withDereference(),
                    this.referenced!!,
                    other.referenced!!
                )
            }

            // Either both are isSelfMarked and therefore equivalent
            // Or other isSelfMarked and this is not, in which case other would be larger
            // and no positive difference is possible.
            return emptySet()
        }
    }

    class Leaf : MarkableMemory {
        override var isMarked: Boolean = false

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            throw IllegalStateException("No memory accesses are possible on an atomic non-reference member.")
        }

        internal fun subtractMarked(current: MemoryPath, other: Leaf): Collection<MemoryPath> {
            if (!other.isMarked) return setOf(current)
            return emptySet()
        }
    }

    companion object {

        private fun forMaterializedType(type: Type.MaterializedType): MarkableMemory = when (type) {
            is Type.Builtin -> Leaf()
            is Type.Reference -> Reference(type)
            is Type.Structure -> Structure(type)
        }

        fun unmarkedForType(type: Type): MarkableMemory =
            type.applyOnMaterialized(Leaf(), ::forMaterializedType)

        fun subtract(
            root: ResourcePath,
            minuend: MarkableMemory,
            subtrahend: MarkableMemory
        ): Collection<MemoryPath> =
            subtract(MemoryPath.ofDereferencedResource(root), minuend, subtrahend)

        internal fun subtract(
            current: MemoryPath,
            minuend: MarkableMemory,
            subtrahend: MarkableMemory
        ): Collection<MemoryPath> {
            if (!minuend.isMarked) { // No positive difference possible, if minuend is not marked.
                return emptySet()
            }
            return when (minuend) {
                is Leaf -> minuend.subtractMarked(current, subtrahend as Leaf)
                is Reference -> minuend.subtractMarked(current, subtrahend as Reference)
                is Structure -> minuend.subtractMarked(current, subtrahend as Structure)
            }
        }
    }
}