package ara.types

import ara.syntax.Syntax

object Builtins {

    private val builtinTypes = mapOf(
        "Int" to Type.Integer
    )

    fun environment(): Environment {
        val result = Environment()
        for ((name, type) in builtinTypes) {
            result.defineType(Syntax.Identifier(name), type)
        }
        return result
    }
}