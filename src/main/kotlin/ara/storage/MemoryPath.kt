package ara.storage

import java.util.*

class MemoryPath
private constructor(
    val resource: ResourcePath,
    val path: List<Segment>
) {
    sealed interface Segment
    object Dereference : Segment
    data class Member(val name: String) : Segment

    fun withDereference(): MemoryPath =
        MemoryPath(resource, path + Dereference)

    fun withAccessedMember(member: String): MemoryPath =
        MemoryPath(resource, path + Member(member))


    override fun equals(other: Any?): Boolean =
        other is MemoryPath && other.resource == this.resource && other.path == this.path

    override fun hashCode(): Int =
        Objects.hash(resource, path)

    override fun toString(): String =
        resource.toString() + path.joinToString(separator = "") { formatMemoryAccessor(it) }

    companion object {
        fun ofDereferencedResource(resource: ResourcePath): MemoryPath =
            MemoryPath(resource, listOf(Dereference))

        private fun formatMemoryAccessor(accessor: Segment): String = when (accessor) {
            Dereference -> "&"
            is Member -> "." + accessor.name
        }
    }
}