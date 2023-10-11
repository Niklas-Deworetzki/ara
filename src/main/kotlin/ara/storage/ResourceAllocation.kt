package ara.storage

import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.Storage.asResourcePath(): ResourcePath = when (this) {
        is Syntax.NamedStorage ->
            ResourcePath.ofIdentifier(this.name)

        is Syntax.MemberAccess ->
            this.storage.asResourcePath().withAccessedMember(this.member)

        is Syntax.TypedStorage ->
            this.storage.asResourcePath()
    }


    fun Syntax.ResourceExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.Storage ->
            listOf(this.asResourcePath())

        is Syntax.AllocationExpression ->
            this.value.asResourcePaths()

        is Syntax.StructureLiteral ->
            this.members.flatMap { it.value.asResourcePaths() }

        is Syntax.IntegerLiteral, is Syntax.Memory ->
            emptyList()
    }

    fun Syntax.Instruction.variablesCreated(): Collection<ResourcePath> =
        this.resourcesCreated().flatMap { it.asResourcePaths() }

    fun Syntax.Instruction.resourcesCreated(): Collection<Syntax.ResourceExpression> = when (this) {
        is Syntax.ArithmeticAssignment ->
            listOf(this.dst)

        is Syntax.MultiAssignment ->
            this.dstList

        is Syntax.Call ->
            this.dstList

        is Syntax.Conditional, is Syntax.Unconditional ->
            emptyList()
    }

    fun Syntax.Instruction.variablesDestroyed(): Collection<ResourcePath> =
        this.resourcesDestroyed().flatMap { it.asResourcePaths() }

    fun Syntax.Instruction.resourcesDestroyed(): Collection<Syntax.ResourceExpression> = when (this) {
        is Syntax.ArithmeticAssignment ->
            listOf(this.src)

        is Syntax.MultiAssignment ->
            this.srcList

        is Syntax.Call ->
            this.srcList

        is Syntax.Conditional, is Syntax.Unconditional ->
            emptyList()
    }

    fun Syntax.Instruction.resources(): Collection<Syntax.ResourceExpression> =
        resourcesCreated() + resourcesDestroyed()

    fun Syntax.ArithmeticExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.ArithmeticBinary ->
            this.lhs.asResourcePaths() + this.rhs.asResourcePaths()

        is Syntax.ArithmeticValue ->
            this.value.asResourcePaths()
    }

    fun Syntax.ConditionalExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.ComparativeBinary ->
            this.lhs.asResourcePaths() + this.rhs.asResourcePaths()
    }

    fun Syntax.Memory.asMemoryPath(): MemoryPath = when (this) {
        is Syntax.DereferencedStorage ->
            MemoryPath.ofDereferencedResource(this.storage.asResourcePath())

        is Syntax.DereferencedMemory ->
            this.asMemoryPath().withDereference()

        is Syntax.MemoryMemberAccess ->
            this.memory.asMemoryPath().withAccessedMember(this.member)
    }
}