package ara.interpreter

import ara.types.Type
import ara.utils.NonEmptyList
import ara.utils.NonEmptyList.Companion.toNonEmptyList
import ara.utils.zip
import java.lang.IllegalArgumentException

sealed interface Value {

    object Unit : Value {
        override fun toString(): String =
            "{ }"
    }

    data class Integer(val value: Int) : Value {
        override fun toString(): String =
            value.toString()
    }

    data class Structure(val members: NonEmptyList<Member>) : Value {
        override fun toString(): String =
            members.joinToString(separator = ", ", prefix = "{", postfix = "}") {
                "${it.name} = ${it.value}"
            }
    }

    data class Member(override val key: String, override val value: Value) : Map.Entry<String, Value> {
        val name: String
            get() = key
    }

    companion object {
        val ZERO: Value = Integer(0)

        fun defaultValueForType(type: Type): Value =
            DefaultValueAlgebra.evaluate(type)

        private object DefaultValueAlgebra : Type.Algebra<Value> {
            override fun builtin(builtin: Type.BuiltinType): Value = when (builtin) {
                Type.Comparison -> throw NotImplementedError("Cannot store comparison results for now.")
                Type.Integer -> ZERO
                Type.Unit -> Unit
            }

            override fun structure(memberNames: NonEmptyList<String>, memberValues: NonEmptyList<Value>): Value {
                val members = zip(memberNames, memberValues, ::Member)
                return Structure(members.toNonEmptyList())
            }

            override fun uninitializedVariable(): Value =
                throw IllegalArgumentException("Cannot provide value for uninitialized type variable.")
        }
    }
}