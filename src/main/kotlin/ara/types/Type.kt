package ara.types

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


    abstract class BuiltinType : Type()

    object Integer : BuiltinType()

    object Comparison : BuiltinType()


    data class Structure(val members: List<Member>) : Type()

    data class Member(
        val name: String,
        val type: Type
    )


    operator fun contains(other: Variable): Boolean = when (this) {
        is BuiltinType -> false
        is Structure -> members.any { it.type.contains(other) }
        is Variable -> fold({ this === other }) {
            it.contains(other)
        }
    }


    interface Algebra<T> {
        fun builtin(builtin: BuiltinType): T
        fun structure(memberNames: List<String>, memberValues: List<T>): T
        fun uninitializedVariable(): T

        companion object {
            fun <T> Algebra<T>.evaluate(type: Type): T = when (type) {
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
}
