package ara.analysis

import ara.analysis.type.TypeComputation
import ara.syntax.Syntax
import ara.syntax.extensions.types
import ara.types.TypeUnification
import ara.types.TypeUnification.unify

/**
 * Analysis pass collecting all user-defined types.
 */
class TypeDefinitionAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val typeDefinitions = program.types

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
            reportError("Type ${definition.name} was defined multiple times.")
                .withPositionOf(definition)
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
                    reportError("Unable to construct infinite type ${definition.name}.")
                        .withPositionOf(definition.name)

                else ->
                    reportError("Unable to declare type ${definition.name}.")
                        .withPositionOf(definition.name)
            }
        }
    }
}