package ara.storage

import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.Storage.asResourcePath(): ResourcePath = when (this) {
        is Syntax.NamedStorage ->
            ResourcePath.ofIdentifier(this.name)

        is Syntax.TypedStorage ->
            this.storage.asResourcePath()

        is Syntax.MemberAccess ->
            this.storage.asResourcePath().appended(this.member)
    }

    fun Syntax.ResourceExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.Storage ->
            listOf(this.asResourcePath())

        is Syntax.IntegerLiteral ->
            emptyList()

        is Syntax.StructureLiteral ->
            this.members.flatMap { it.value.asResourcePaths() }
    }

    fun Syntax.Instruction.variablesCreated(): Collection<ResourcePath> =
        this.resourcesCreated().flatMap { it.asResourcePaths() }

    fun Syntax.Instruction.resourcesCreated(): Collection<Syntax.ResourceExpression> = when (this) {
        is Syntax.Assignment ->
            listOf(this.dst)

        is Syntax.Call ->
            this.dstList

        else ->
            emptyList()
    }

    fun Syntax.Instruction.variablesDestroyed(): Collection<ResourcePath> =
        this.resourcesDestroyed().flatMap { it.asResourcePaths() }

    fun Syntax.Instruction.resourcesDestroyed(): Collection<Syntax.ResourceExpression> = when (this) {
        is Syntax.Assignment ->
            listOf(this.src)

        is Syntax.Call ->
            this.srcList

        else ->
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

    fun Syntax.Instruction.variablesUsed(): Collection<ResourcePath> = when (this) {
        is Syntax.Assignment ->
            this.arithmetic?.value?.asResourcePaths() ?: emptySet()

        is Syntax.Call ->
            emptySet()

        is Syntax.Conditional ->
            this.condition.asResourcePaths()

        is Syntax.Unconditional ->
            emptySet()
    }
}