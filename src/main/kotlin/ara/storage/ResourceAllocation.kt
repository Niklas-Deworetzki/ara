package ara.storage

import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.Storage.asResourcePath(): ResourcePath {
        val segments = ArrayDeque<String>()
        var storage = this

        while (true) {
            when (storage) {
                is Syntax.NamedStorage -> {
                    segments.addFirst(storage.name.name)
                    return ResourcePath.of(segments)
                }

                is Syntax.MemberAccess -> {
                    segments.addFirst(storage.member.name)
                    storage = storage.storage
                }

                is Syntax.DereferencedStorage -> {
                    segments.clear()
                    storage = storage.storage
                }

                is Syntax.TypedStorage -> {
                    storage = storage.storage
                }
            }
        }
    }

    fun Syntax.ResourceExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.Storage ->
            listOf(this.asResourcePath())

        is Syntax.AllocationExpression ->
            this.value.asResourcePaths()

        is Syntax.IntegerLiteral ->
            emptyList()

        is Syntax.StructureLiteral ->
            this.members.flatMap { it.value.asResourcePaths() }
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
}