package ara.analysis.live

import ara.analysis.live.LivenessState.*
import ara.position.Range
import ara.storage.ResourcePath
import ara.storage.StorageDescriptor
import ara.syntax.Syntax

class LivenessDescriptor : StorageDescriptor<LivenessState> {

    constructor(routine: Syntax.RoutineDefinition) : super(fromEnvironment(routine.localEnvironment, Unknown))

    private constructor(root: DescriptorNode<LivenessState>) : super(root)

    fun copy(): LivenessDescriptor =
        LivenessDescriptor(root)


    operator fun plusAssign(initializer: Pair<ResourcePath, Range>) =
        set(initializer.first, Initialized(listOf(initializer.second)))

    operator fun minusAssign(finalizer: Pair<ResourcePath, Range>) =
        set(finalizer.first, Finalized(listOf(finalizer.second)))

    override fun setNodeValue(node: DescriptorNode<LivenessState>, value: LivenessState): Unit = when (node) {
        is LeafNode ->
            node.data = node.data overwrittenWith value

        is InnerNode ->
            for (subNode in node) {
                setNodeValue(subNode, value)
            }
    }

    private fun allLeafValues(
        node: DescriptorNode<LivenessState>,
        buffer: MutableList<LivenessState> = mutableListOf()
    ): List<LivenessState> = when (node) {
        is LeafNode -> {
            buffer.add(node.data)
            buffer
        }

        is InnerNode -> {
            for (subNode in node) {
                allLeafValues(subNode, buffer)
            }
            buffer
        }
    }

    override fun getNodeValue(node: DescriptorNode<LivenessState>): LivenessState = when (node) {
        is LeafNode ->
            node.data

        is InnerNode -> {
            val initializers = mutableListOf<Range>()
            val finalizers = mutableListOf<Range>()

            for (value in allLeafValues(node)) {
                when (value) {
                    is Initialized ->
                        initializers.addAll(value.initializers)

                    is Finalized ->
                        finalizers.addAll(value.finalizers)

                    Unknown ->
                        Unit // Do nothing.

                    is Conflict -> {
                        initializers.addAll(value.initializers)
                        finalizers.addAll(value.finalizers)
                    }
                }
            }

            when {
                initializers.isNotEmpty() && finalizers.isNotEmpty() ->
                    Conflict(initializers, finalizers)

                initializers.isNotEmpty() ->
                    Initialized(initializers)

                finalizers.isNotEmpty() ->
                    Finalized(finalizers)

                else ->
                    Unknown
            }
        }
    }

    override fun formatValue(value: LivenessState): String = when (value) {
        Unknown -> "?"
        is Initialized -> "+"
        is Finalized -> "-"
        is Conflict -> "!"
    }

    private infix fun LivenessState.overwrittenWith(other: LivenessState): LivenessState = when (this) {
        Unknown ->
            other

        is Initialized ->
            if (other is Initialized) Initialized(this.initializers + other.initializers)
            else other

        is Finalized ->
            if (other is Finalized) Finalized(this.finalizers + other.finalizers)
            else other

        is Conflict ->
            when (other) {
                is Conflict ->
                    Conflict(initializers + other.initializers, finalizers + other.finalizers)

                is Initialized ->
                    Conflict(initializers + other.initializers, finalizers)

                is Finalized ->
                    Conflict(initializers, finalizers + other.finalizers)

                Unknown ->
                    this
            }
    }
}