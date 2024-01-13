package ara.storage.extensions

import ara.storage.MemoryPath
import ara.storage.ResourceAllocation.asResourcePath
import ara.syntax.Syntax

object ForMemoryPaths {

    @JvmStatic
    fun allMemoryReferences(expression: Syntax.ResourceExpression): Collection<Syntax.Memory> = when (expression) {
        is Syntax.AllocationExpression ->
            allMemoryReferences(expression.value)

        is Syntax.Memory ->
            listOf(expression)

        is Syntax.StructureLiteral ->
            expression.members.flatMap { allMemoryReferences(it.value) }

        is Syntax.IntegerLiteral,
        is Syntax.NullReferenceLiteral,
        is Syntax.Storage ->
            emptyList()
    }

    @JvmStatic
    fun asMemoryPaths(expression: Syntax.ResourceExpression): Collection<MemoryPath> =
        allMemoryReferences(expression).map { asMemoryPath(it) }

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