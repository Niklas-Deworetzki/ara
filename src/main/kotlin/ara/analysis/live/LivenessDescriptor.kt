package ara.analysis.live

import ara.storage.StorageDescriptor
import ara.syntax.Syntax

class LivenessDescriptor(routine: Syntax.RoutineDefinition) :
    StorageDescriptor<Boolean?>(fromEnvironment(routine.localEnvironment, null)) {

    override fun setSyntheticValue(node: DescriptorNode<Boolean?>, value: Boolean?): Unit = when (node) {
        is LeafNode ->
            node.data = value

        is InnerNode ->
            for (subNode in node.data.values) {
                setSyntheticValue(subNode, value)
            }
    }

    override fun getSyntheticValue(node: DescriptorNode<Boolean?>): Boolean? = when (node) {
        is LeafNode ->
            node.data

        is InnerNode ->
            node.data.values.any { subNode ->
                getSyntheticValue(subNode) ?: false
            }
    }

    override fun formatValue(value: Boolean?): String = when (value) {
        true -> "+"
        false -> "-"
        null -> "?"
    }
}