package ara.interpreter

import ara.types.Type
import ara.types.Type.Algebra.Companion.evaluate
import ara.utils.zipToMap

sealed interface Value {

    data class Integer(val value: Int) : Value {
        override fun toString(): String =
            value.toString()
    }

    data class Structure(val members: Map<String, Value>) : Value {
        override fun toString(): String = when {
            members.isEmpty() -> "{ }"
            else -> members.asIterable().joinToString(separator = ", ", prefix = "{ ", postfix = " }") {
                "${it.key} = ${it.value}"
            }
        }
    }

    companion object {
        val ZERO: Value = Integer(0)

        fun defaultValueForType(type: Type): Value =
            DefaultValueAlgebra.evaluate(type)

        private object DefaultValueAlgebra : Type.Algebra<Value> {
            override fun builtin(builtin: Type.BuiltinType): Value =
                ZERO

            override fun structure(memberNames: List<String>, memberValues: List<Value>): Value =
                Structure(zipToMap(memberNames, memberValues))

            override fun uninitializedVariable(): Value =
                ZERO
        }
    }
}