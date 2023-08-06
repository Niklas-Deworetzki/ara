package ara.interpreter

import ara.Direction
import ara.reporting.Message.Companion.quoted
import ara.storage.StorageDescriptor
import ara.syntax.Syntax
import java.util.*

class StackFrame(val direction: Direction, val routine: Syntax.RoutineDefinition, val caller: Syntax.Call? = null) :
    StorageDescriptor<Value>(fromEnvironment(routine.localEnvironment, Value.ZERO)) {

    val queuedInstructions: Queue<Syntax.Instruction> = ArrayDeque()

    override fun setNodeValue(node: DescriptorNode<Value>, value: Value) {
        when (node) {
            is InnerNode -> {
                if (value !is Value.Structure)
                    throw InternalInconsistencyException("Structure value is required for structure assignment.")

                node.data.forEach { (key, member) ->
                    val memberValue = value.members[key]
                        ?: throw InternalInconsistencyException("Member ${key.quoted()} is not present in assigned data.")
                    setNodeValue(member, memberValue)
                }
            }


            is LeafNode ->
                node.data = value
        }
    }

    override fun getNodeValue(node: DescriptorNode<Value>): Value = when (node) {
        is InnerNode -> {
            val members = mutableMapOf<String, Value>()
            node.data.forEach { (name, value) ->
                members[name] = getNodeValue(value)
            }
            Value.Structure(members)
        }

        is LeafNode ->
            node.data
    }
}
