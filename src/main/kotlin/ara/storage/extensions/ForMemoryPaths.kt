package ara.storage.extensions

import ara.storage.MemoryPath
import ara.storage.ResourceAllocation.asResourcePath
import ara.syntax.Syntax

object ForMemoryPaths {

    @JvmStatic
    fun asMemoryPaths(expression: Syntax.ResourceExpression): Collection<MemoryPath> = when (expression) {
        is Syntax.AllocationExpression ->
            asMemoryPaths(expression.value)

        is Syntax.Memory ->
            listOf(asMemoryPath(expression))

        is Syntax.StructureLiteral ->
            expression.members.flatMap { asMemoryPaths(it.value) }

        is Syntax.IntegerLiteral,
        is Syntax.MemberAccess,
        is Syntax.NamedStorage,
        is Syntax.TypedStorage ->
            emptyList()
    }

    @JvmStatic
    fun asMemoryPath(memory: Syntax.Memory): MemoryPath = when (memory) {
        is Syntax.DereferencedStorage ->
            MemoryPath.ofDereferencedResource(memory.storage.asResourcePath())

        is Syntax.DereferencedMemory ->
            asMemoryPath(memory).withDereference()

        is Syntax.MemoryMemberAccess ->
            asMemoryPath(memory.memory).withAccessedMember(memory.member.name)
    }
}