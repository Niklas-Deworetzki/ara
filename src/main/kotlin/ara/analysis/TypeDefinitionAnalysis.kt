package ara.analysis

import ara.syntax.Syntax
import ara.types.TypeUnification
import ara.types.TypeUnification.unify

class TypeDefinitionAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val typeDefinitions = program.definitions.filterIsInstance<Syntax.TypeDefinition>()

        typeDefinitions.forEach(::declareType)
        proceedAnalysis {
            typeDefinitions.forEach(::computeDefinedType)
        }

        debug { showDefinedTypes(typeDefinitions) }
    }

    private fun showDefinedTypes(typeDefinitions: List<Syntax.TypeDefinition>): String = when {
        typeDefinitions.isEmpty() -> "No user defined types are present."

        else -> typeDefinitions.joinToString("\n") {
            val name = it.name
            val type = program.environment.getType(name)
            "$name defined as $type"
        }
    }

    private fun declareType(definition: Syntax.TypeDefinition) {
        if (!program.environment.declareType(definition.name)) {
            reportError(definition, "Type ${definition.name} is defined multiple times.")
        }
    }

    private fun computeDefinedType(
        definition: Syntax.TypeDefinition
    ) {
        val context = TypeComputation(program.environment, definition.type)
        val computedType = includeAnalysis(context)

        if (!context.hasReportedErrors) {
            val declaredType = program.environment.getType(definition.name)!!
            when (unify(declaredType, computedType)) {
                null ->
                    Unit

                is TypeUnification.Error.RecursiveType ->
                    reportError(definition.name, "Unable to construct infinite type ${definition.name}.")

                else ->
                    reportError(definition.name, "Unable declare type ${definition.name}.")
            }
        }
    }
}