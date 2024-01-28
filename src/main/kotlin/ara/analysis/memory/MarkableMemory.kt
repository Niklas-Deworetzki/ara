package ara.analysis.memory

import ara.storage.MemoryPath
import ara.storage.ResourcePath
import ara.types.Type
import ara.utils.formatting.formatToHumanReadable

sealed interface MarkableMemory {

    fun isMarked(): Boolean

    fun setMarked()

    fun access(segment: MemoryPath.Segment): MarkableMemory

    class Structure(type: Type.Structure) : MarkableMemory {
        private val members = type.members.associate {
            it.name to unmarkedForType(it.type)
        }

        override fun isMarked(): Boolean =
            members.values.any { it.isMarked() }

        override fun setMarked() =
            members.values.forEach { it.setMarked() }

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            require(segment is MemoryPath.Member) { "Structure can only be accessed via member access operation." }
            require(segment.name in members.keys) {
                val validMemberNames = members.keys.formatToHumanReadable(", ", ", or ")
                "Member name must be one of $validMemberNames."
            }
            return members[segment.name]!!
        }

        internal fun subtractFromMarked(current: MemoryPath, other: Structure): Collection<MemoryPath> {
            if (!other.isMarked() && members.values.all { it.isMarked() }) {
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

        override fun isMarked(): Boolean =
            referenced?.isMarked() ?: isSelfMarked

        override fun setMarked() {
            isSelfMarked = true
            referenced = null
        }

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            require(segment is MemoryPath.Dereference) { "Reference can only be accessed via dereference operation." }
            if (referenced == null) {
                referenced = makeReference()
            }
            return referenced!!
        }

        private fun makeReference() =
            unmarkedForType(type.base)

        internal fun subtractFromMarked(current: MemoryPath, other: Reference): Collection<MemoryPath> = when {
            other.isSelfMarked ->
                // Other is larger or equal to this, therefore no positive difference is possible.
                emptySet()

            this.isSelfMarked ->
                // This must always be larger, as other.isSelfMarked cannot be true.
                setOf(current)

            else ->
                subtract(
                    current.withDereference(),
                    this.referenced!!,
                    other.referenced ?: makeReference()
                )
        }
    }

    class Leaf : MarkableMemory {
        private var isMarked: Boolean = false

        override fun isMarked(): Boolean = isMarked

        override fun setMarked() {
            isMarked = true
        }

        override fun access(segment: MemoryPath.Segment): MarkableMemory {
            throw IllegalStateException("No memory accesses are possible on an atomic non-reference member.")
        }

        internal fun subtractFromMarked(current: MemoryPath, other: Leaf): Collection<MemoryPath> {
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
            subtract(MemoryPath(root, emptyList()), minuend, subtrahend)

        internal fun subtract(
            current: MemoryPath,
            minuend: MarkableMemory,
            subtrahend: MarkableMemory
        ): Collection<MemoryPath> {
            if (!minuend.isMarked()) { // No positive difference possible, if minuend is not marked.
                return emptySet()
            }
            return when (minuend) {
                is Leaf -> minuend.subtractFromMarked(current, subtrahend as Leaf)
                is Reference -> minuend.subtractFromMarked(current, subtrahend as Reference)
                is Structure -> minuend.subtractFromMarked(current, subtrahend as Structure)
            }
        }
    }
}