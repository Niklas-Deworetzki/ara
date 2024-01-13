package ara.analysis.memory

import ara.storage.MemoryPath
import ara.storage.StorageDescriptor
import ara.types.Environment
import ara.types.Type

class MarkableMemoryDescriptor(environment: Environment) :
    StorageDescriptor<MarkableMemory?>(
        fromEnvironment(environment, MarkableMemoryDescriptorConstructionAlgebra)
    ) {

    fun mark(memoryPath: MemoryPath): Boolean {
        val memory = getAccessedMemory(memoryPath)

        val hasBeenMarkedBefore = memory.isMarked()
        memory.setMarked()
        return hasBeenMarkedBefore
    }

    operator fun minus(other: MarkableMemoryDescriptor): Collection<MemoryPath> {
        val result = ArrayList<MemoryPath>()
        for (key in this.keys) {
            val thisLeaf = this.findNode(key)
            if (thisLeaf is LeafNode && thisLeaf.data != null) {
                val otherLeaf = other.findNode(key) as LeafNode

                val difference = MarkableMemory.subtract(
                    key,
                    thisLeaf.data!!,
                    otherLeaf.data!!
                )
                result.addAll(difference)
            }
        }

        return result
    }

    private fun getAccessedMemory(memoryPath: MemoryPath): MarkableMemory {
        val node = findNode(memoryPath.resource)
        require(node is LeafNode) { "Memory access on structure is not possible." }
        val accessedMemory = requireNotNull(node.data) { "Memory access on non-reference value is not possible." }
        return memoryPath.path.fold(accessedMemory, MarkableMemory::access)
    }

    private object MarkableMemoryDescriptorConstructionAlgebra :
        DescriptorConstructionAlgebra<MarkableMemory?>() {

        override fun createForMaterializedType(type: Type.MaterializedType): MarkableMemory? = when (type) {
            is Type.Reference -> MarkableMemory.unmarkedForType(type)
            else -> null
        }

        override fun createForUninitializedType(): MarkableMemory? =
            null
    }
}