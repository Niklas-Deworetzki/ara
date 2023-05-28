package ara.storage

import ara.syntax.Syntax

object ResourceAllocation {

    fun Syntax.ResourceExpression.createdResource(): ResourcePath? = when (this) {
        is Syntax.MemberAccess -> {
            val result = this.storage.createdResource()
            result?.appended(this.member.name)
        }

        is Syntax.NamedStorage ->
            ResourcePath.localRoot(this.name.name)

        is Syntax.TypedStorage ->
            this.storage.createdResource()

        is Syntax.IntegerLiteral ->
            null
    }

    fun Syntax.Instruction.variablesCreated(): Collection<ResourcePath> = when (this) {
        is Syntax.Assignment ->
            setOfNotNull(this.dst.createdResource())

        is Syntax.Call ->
            this.dstList.mapNotNull { it.createdResource() }

        else ->
            emptyList()
    }

    fun Syntax.Instruction.variablesDestroyed(): Collection<ResourcePath> = when (this) {
        is Syntax.Assignment ->
            setOfNotNull(this.src.createdResource())

        is Syntax.Call ->
            this.srcList.mapNotNull { it.createdResource() }

        else ->
            emptyList()
    }
}