package ara.interpreter

sealed interface Value {

    data class Integer(val value: Int) : Value

    data class Structure(val members: Map<String, Value>) : Value

    companion object {
        val ZERO: Value = Integer(0)
    }
}