package ara.analysis

import ara.analysis.type.TypeCheckingMixin
import ara.analysis.type.TypeComputation.Companion.computedType
import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.extensions.lookupVariableType
import ara.types.Signature

/**
 * Analysis pass used to verify that all parameters have a well-defined type.
 */
class ParameterTypeAnalysis(private val program: Syntax.Program) : Analysis<Unit>(), TypeCheckingMixin {

    override fun reportTypeError(message: Message): Message =
        reportError(message)

    override fun runAnalysis() = forEachRoutineIn(program) {
        verifyParameterLists(routine)
    }

    private fun verifyParameterLists(routine: Syntax.RoutineDefinition) {
        ParameterListTyper(routine).defineTypesForParameters()

        ensureParametersHaveInstantiatedTypes(routine)
        routine.signature = getRoutineSignature(routine)
    }

    private fun ensureParametersHaveInstantiatedTypes(routine: Syntax.RoutineDefinition) {
        val parameters = getParameterNames(routine)
        parameters.ensureInstantiated(routine, "parameter")
    }

    private fun getParameterNames(routine: Syntax.RoutineDefinition): Set<Syntax.Identifier> =
        (routine.inputParameters + routine.outputParameters)
            .map { parameter -> parameter.name }
            .toSet()

    private fun getRoutineSignature(routine: Syntax.RoutineDefinition): Signature {
        val inputTypes = routine.inputParameters.map {
            routine.lookupVariableType(it.name)!!
        }
        val outputTypes = routine.outputParameters.map {
            routine.lookupVariableType(it.name)!!
        }
        return Signature(inputTypes, outputTypes)
    }

    private inner class ParameterListTyper(private val routine: Syntax.RoutineDefinition) {

        fun defineTypesForParameters() {
            for (parameter in routine.inputParameters + routine.outputParameters) {
                if (parameter.type == null) continue

                val variableType = routine.lookupVariableType(parameter.name)!!
                val declaredType = computedType(parameter.type, routine.localEnvironment)

                variableType.isDefinedAs(declaredType, parameter.type) {
                    "Type defined for parameter is not compatible with actual type."
                }
            }
        }
    }
}