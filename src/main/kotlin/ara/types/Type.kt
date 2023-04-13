package ara.types

sealed class Type {

    abstract operator fun contains(other: Variable): Boolean

    data class Variable(var type: Type? = null) : Type() {

        inline fun <T> fold(zero: T, function: (Type) -> T): T = when (type) {
            null -> zero
            else -> function(type!!)
        }

        override fun contains(other: Variable): Boolean =
            fold(this === other) {
                it.contains(other)
            }

        override fun equals(other: Any?): Boolean {
            if (type != null) {
                return type!! == other
            } else if (other is Variable) {
                return other.fold(this === other, this::equals)
            }
            return false
        }

        override fun hashCode(): Int = fold(0) {
            it.hashCode()
        }
    }


    abstract class BuiltinType : Type() {
        override fun contains(other: Variable): Boolean = false
    }

    object Integer : BuiltinType()

    object Comparison : BuiltinType()

    data class Reference(
        val base: Type
    ) : Type() {
        override fun contains(other: Variable): Boolean =
            base.contains(other)
    }

    data class Structure(
        val members: List<Member>
    ) : Type() {
        override fun contains(other: Variable): Boolean = members.any {
            it.type.contains(other)
        }
    }

    data class Member(
        val name: String,
        val type: Type
    )
}
