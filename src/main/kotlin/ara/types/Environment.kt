package ara.types

import ara.syntax.Syntax

class Environment(val outer: Environment? = null) {
    private val routineMap = mutableMapOf<Syntax.Identifier, Syntax.RoutineDefinition>()
    private val typeMap = mutableMapOf<Syntax.Identifier, Type>()
    private val variableMap = mutableMapOf<Syntax.Identifier, Type>()


    val variables: Sequence<Map.Entry<Syntax.Identifier, Type>>
        get() = variableMap.asSequence()


    fun getRoutine(name: Syntax.Identifier): Syntax.RoutineDefinition? =
        lookupFromMap(this, Environment::routineMap, name)

    fun getType(name: Syntax.Identifier): Type? =
        lookupFromMap(this, Environment::typeMap, name)

    fun getVariable(name: Syntax.Identifier): Type? =
        lookupFromMap(this, Environment::variableMap, name)


    fun defineRoutine(definition: Syntax.RoutineDefinition): Boolean {
        return routineMap.putIfAbsent(definition.name, definition) == null
    }

    fun defineType(name: Syntax.Identifier, type: Type): Boolean {
        return typeMap.putIfAbsent(name, type) == null
    }

    fun declareType(name: Syntax.Identifier): Boolean {
        return typeMap.putIfAbsent(name, Type.Variable()) == null
    }

    fun declareVariable(name: Syntax.Identifier) {
        variableMap.putIfAbsent(name, Type.Variable())
    }

    companion object {
        private tailrec fun <K, V> lookupFromMap(
            environment: Environment,
            member: (Environment) -> Map<K, V>,
            key: K
        ): V? {
            val result = member(environment)[key]
            return when {
                result == null && environment.outer != null ->
                    lookupFromMap(environment.outer, member, key)

                else ->
                    result
            }
        }
    }
}