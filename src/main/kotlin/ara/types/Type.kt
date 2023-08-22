package ara.types

import ara.utils.NonEmptyList
import ara.utils.NonEmptyList.Companion.toNonEmptyList

sealed class Type {
    data class Variable(var type: Type? = null) : Type() {
        inline fun <T> fold(zero: () -> T, function: (Type) -> T): T = when (val containedType = type) {
            null -> zero()
            else -> function(containedType)
        }

        override fun equals(other: Any?): Boolean {
            if (type != null) {
                return type!! == other
            } else if (other is Variable) {
                return other.fold({ this === other }, this::equals)
            }
            return false
        }

        override fun hashCode(): Int = fold({ 0 }) {
            it.hashCode()
        }
    }


    sealed class BuiltinType(val representation: String) : Type()

    object Integer : BuiltinType("Int")

    object Comparison : BuiltinType("Comparison")

    object Unit : BuiltinType("{ }")

    data class Structure(val members: NonEmptyList<Member>) : Type()

    data class Member(val name: String, val type: Type)


    operator fun contains(other: Variable): Boolean = when (this) {
        is BuiltinType -> false
        is Structure -> members.any { it.type.contains(other) }
        is Variable -> fold({ this === other }) {
            it.contains(other)
        }
    }

    final override fun toString(): String = Show.evaluate(this)

    private object Show : Algebra<String> {
        override fun builtin(builtin: BuiltinType): String =
            builtin.representation

        override fun structure(memberNames: NonEmptyList<String>, memberValues: NonEmptyList<String>): String =
            memberNames.zip(memberValues)
                .joinToString(prefix = "{", separator = ",", postfix = "}") { (name, value) -> "$name: $value" }

        override fun uninitializedVariable(): String = "⊥"
    }


    companion object {
        fun fromMembers(members: List<Member>): Type = when {
            members.isEmpty() -> Unit
            else -> Structure(members.toNonEmptyList())
        }
    }

    interface Algebra<T> {
        fun builtin(builtin: BuiltinType): T
        fun structure(memberNames: NonEmptyList<String>, memberValues: NonEmptyList<T>): T
        fun uninitializedVariable(): T

        fun evaluate(type: Type): T = when (type) {
            is BuiltinType ->
                builtin(type)

            is Structure ->
                structure(
                    type.members.map { it.name },
                    type.members.map { evaluate(it.type) }
                )

            is Variable ->
                type.fold(this::uninitializedVariable) {
                    evaluate(it)
                }
        }
    }
}
