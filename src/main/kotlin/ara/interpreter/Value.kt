package ara.interpreter

import ara.types.Type
import ara.utils.zipToMap
import ara.types.Type.Algebra.Companion.evaluate

sealed interface Value {

    data class Integer(val value: Int) : Value

    data class Structure(val members: Map<String, Value>) : Value

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