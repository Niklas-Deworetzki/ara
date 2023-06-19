package ara.storage

import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.ResourceExpression.asResourcePath(): ResourcePath? = when (this) {
        is Syntax.MemberAccess -> {
            val result = this.storage.asResourcePath()
            result?.appended(this.member)
        }

        is Syntax.NamedStorage ->
            ResourcePath.ofIdentifier(this.name)

        is Syntax.TypedStorage ->
            this.storage.asResourcePath()

        is Syntax.IntegerLiteral ->
            null
    }

    fun Syntax.Instruction.variablesCreated(): Collection<ResourcePath> =
        resourcesCreated().mapNotNull { it.asResourcePath() }

    fun Syntax.Instruction.resourcesCreated(): Collection<Syntax.ResourceExpression> = when (this) {
        is Syntax.Assignment ->
            listOf(this.dst)

        is Syntax.Call ->
            this.dstList

        else ->
            emptyList()
    }

    fun Syntax.Instruction.variablesDestroyed(): Collection<ResourcePath> =
        this.resourcesDestroyed().mapNotNull { it.asResourcePath() }

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
            listOfNotNull(this.lhs.asResourcePath(), this.rhs.asResourcePath())

        is Syntax.ArithmeticValue ->
            listOfNotNull(this.value.asResourcePath())
    }

    fun Syntax.ConditionalExpression.asResourcePaths(): Collection<ResourcePath> = when (this) {
        is Syntax.ComparativeBinary ->
            listOfNotNull(this.lhs.asResourcePath(), this.rhs.asResourcePath())
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