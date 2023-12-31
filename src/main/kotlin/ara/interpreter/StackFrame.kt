package ara.interpreter

import ara.Direction
import ara.reporting.Message.Companion.quoted
import ara.storage.StorageDescriptor
import ara.syntax.Syntax
import ara.utils.NonEmptyList.Companion.toNonEmptyList
import ara.utils.combineWith
import java.util.*

class StackFrame(val direction: Direction, val routine: Syntax.RoutineDefinition, val caller: Syntax.Call? = null) :
    StorageDescriptor.WithGetSet<Value, Value>(fromEnvironment(routine.localEnvironment, Value.ZERO)) {

    val queuedInstructions: Queue<Syntax.Instruction> = ArrayDeque()

    override fun setNodeValue(node: DescriptorNode<Value>, value: Value) {
        when (node) {
            is InnerNode -> {
                internalAssertion(value is Value.Structure) {
                    "Structure value is required for structure assignment."
                }

                combineWith(node.data, value.members) { entry, member ->
                    internalAssertion(entry.key == member.key) {
                        "Mismatch assigning ${member.name.quoted()} to ${entry.key.quoted()}."
                    }
                    setNodeValue(entry.value, member.value)
                }
            }


            is LeafNode ->
                node.data = value
        }
    }

    override fun getNodeValue(node: DescriptorNode<Value>): Value = when (node) {
        is InnerNode -> {
            val members = node.data.map { (name, value) ->
                Value.Member(name, getNodeValue(value))
            }
            Value.Structure(members.toNonEmptyList())
        }

        is LeafNode ->
            node.data
    }
}
