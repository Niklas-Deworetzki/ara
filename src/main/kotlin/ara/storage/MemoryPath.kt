package ara.storage

import ara.syntax.Syntax
import java.util.*

class MemoryPath
private constructor(
    val resource: ResourcePath,
    private val memoryPath: List<MemoryAccessor>
) {

    override fun equals(other: Any?): Boolean =
        other is MemoryPath && other.resource == this.resource && other.memoryPath == this.memoryPath

    override fun hashCode(): Int =
        Objects.hash(resource, memoryPath)

    override fun toString(): String =
        resource.toString() + memoryPath.joinToString(separator = "") { formatMemoryAccessor(it) }

    fun withDereference(): MemoryPath =
        MemoryPath(resource, memoryPath + Dereference)

    fun withAccessedMember(member: Syntax.Identifier): MemoryPath =
        MemoryPath(resource, memoryPath + Member(member.name))

    sealed interface MemoryAccessor
    object Dereference : MemoryAccessor
    data class Member(val name: String) : MemoryAccessor

    companion object {
        fun ofDereferencedResource(resource: ResourcePath): MemoryPath =
            MemoryPath(resource, emptyList())

        private fun formatMemoryAccessor(accessor: MemoryAccessor): String = when (accessor) {
            Dereference -> "&"
            is Member -> "." + accessor.name
        }
    }
}