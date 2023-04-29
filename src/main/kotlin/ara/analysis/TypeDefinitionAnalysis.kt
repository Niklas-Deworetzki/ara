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
        val computedType = context.runAnalysis()

        if (context.hasReportedErrors) {
            context.reportedErrors
                .forEach(::reportError)
        } else {
            val declaredType = program.environment.getType(definition.name)!!
            when (unify(declaredType, computedType)) {
                null ->
                    Unit

                is TypeUnification.Error.RecursiveType ->
                    reportError(definition, "Unable to construct infinite type ${definition.name}.")

                else ->
                    reportError(definition, "Unable declare type ${definition.name}.")
            }
        }
    }
}