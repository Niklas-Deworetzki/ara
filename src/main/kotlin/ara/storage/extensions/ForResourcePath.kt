package ara.storage.extensions

import ara.storage.ResourcePath
import ara.syntax.Syntax

internal object ForResourcePath {

    @JvmStatic
    fun asResourcePath(storage: Syntax.Storage): ResourcePath = when (storage) {
        is Syntax.NamedStorage ->
            ResourcePath.ofIdentifier(storage.name)

        is Syntax.MemberAccess ->
            asResourcePath(storage.storage).withAccessedMember(storage.member)

        is Syntax.TypedStorage ->
            asResourcePath(storage.storage)
    }

    @JvmStatic
    fun asResourcePaths(expression: Syntax.ResourceExpression): Collection<ResourcePath> = when (expression) {
        is Syntax.Storage ->
            listOf(asResourcePath(expression))

        is Syntax.AllocationExpression ->
            asResourcePaths(expression.value)

        is Syntax.StructureLiteral ->
            expression.members.flatMap { asResourcePaths(it.value) }

        is Syntax.IntegerLiteral, is Syntax.Memory ->
            emptyList()
    }

    @JvmStatic
    fun asResourcePaths(expression: Syntax.ArithmeticExpression): Collection<ResourcePath> = when (expression) {
        is Syntax.ArithmeticBinary ->
            asResourcePaths(expression.lhs) + asResourcePaths(expression.rhs)

        is Syntax.ArithmeticValue ->
            asResourcePaths(expression.value)
    }

    @JvmStatic
    fun asResourcePaths(expression: Syntax.ConditionalExpression): Collection<ResourcePath> = when (expression) {
        is Syntax.ComparativeBinary ->
            asResourcePaths(expression.lhs) + asResourcePaths(expression.rhs)
    }
}