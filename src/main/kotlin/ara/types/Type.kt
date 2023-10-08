package ara.types

import ara.utils.NonEmptyList
import ara.utils.NonEmptyList.Companion.toNonEmptyList

sealed class Type {

    sealed interface MaterializedType

    fun <R> applyOnMaterialized(notInitialized: R, f: (MaterializedType) -> R): R =
        Companion.applyOnMaterialized(this, notInitialized, f)

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

        override fun hashCode(): Int = fold({ super.hashCode() }) {
            it.hashCode()
        }
    }

    data class ResolvedName(val name: String, val type: Type) : Type()

    sealed class Builtin(val representation: String) : Type(), MaterializedType

    object Integer : Builtin("Int")

    object Comparison : Builtin("Comparison")

    object Unit : Builtin("{ }")

    data class Reference(val base: Type) : Type(), MaterializedType

    data class Structure(val members: NonEmptyList<Member>) : Type(), MaterializedType

    data class Member(val name: String, val type: Type)


    operator fun contains(other: Variable): Boolean = when (this) {
        is Builtin, is Reference -> false
        is ResolvedName -> type.contains(other)
        is Structure -> members.any { it.type.contains(other) }
        is Variable -> fold({ this === other }) {
            it.contains(other)
        }
    }

    final override fun toString(): String = when (this) {
        is Builtin ->
            representation

        is Reference ->
            "&$base"

        is ResolvedName ->
            name

        is Structure ->
            members.joinToString(prefix = "{", separator = ",", postfix = "}") { (name, value) -> "$name: $value" }

        is Variable ->
            fold({ "⊥" }, Type::toString)
    }

    companion object {
        fun fromMembers(members: List<Member>): Type = when {
            members.isEmpty() -> Unit
            else -> Structure(members.toNonEmptyList())
        }

        private tailrec fun <R> applyOnMaterialized(type: Type, notInitialized: R, f: (MaterializedType) -> R): R =
            when (type) {
                is MaterializedType ->
                    f(type)

                is ResolvedName ->
                    applyOnMaterialized(type, notInitialized, f)

                is Variable ->
                    if (type.type == null) notInitialized
                    else applyOnMaterialized(type.type!!, notInitialized, f)
            }
    }

    interface Algebra<T> : (Type) -> T {
        fun builtin(builtin: Builtin): T
        fun reference(base: Type): T
        fun structure(memberNames: NonEmptyList<String>, memberValues: NonEmptyList<T>): T
        fun uninitializedVariable(): T

        override fun invoke(type: Type): T =
            evaluate(type)

        fun evaluate(type: Type): T = when (type) {
            is Builtin ->
                builtin(type)

            is Reference ->
                reference(type.base)

            is Structure ->
                structure(
                    type.members.map { it.name },
                    type.members.map { evaluate(it.type) }
                )

            is ResolvedName ->
                evaluate(type.type)

            is Variable ->
                type.fold(this::uninitializedVariable) {
                    evaluate(it)
                }
        }
    }
}
