package ara.storage

import ara.utils.formatting.TreeFormatter

internal class StorageDescriptorFormatter<V>(private val descriptor: StorageDescriptor<V>) :
    TreeFormatter<StorageDescriptor.DescriptorNode<V>, V>() {

    override fun isLeafNode(node: StorageDescriptor.DescriptorNode<V>): Boolean =
        node is StorageDescriptor.LeafNode<V>

    override fun extractValue(node: StorageDescriptor.DescriptorNode<V>): V =
        (node as StorageDescriptor.LeafNode<V>).data

    override fun extractChildren(node: StorageDescriptor.DescriptorNode<V>):
            List<Map.Entry<String, StorageDescriptor.DescriptorNode<V>>> =
        (node as StorageDescriptor.InnerNode<V>).data

    override fun formatValue(value: V): String =
        descriptor.formatValue(value)

    fun format(): String =
        formatTree(descriptor.root)
}