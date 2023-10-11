package ara.analysis.live

import ara.analysis.Analysis
import ara.position.Range
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.syntax.extensions.isEmpty

class RoutineLevelResourceAnalysis(val routine: Syntax.RoutineDefinition) : Analysis<Unit>() {
    private val allVariableNames = routine.localEnvironment.variableNames.toSet()
    private val outputVariableNames = routine.outputParameters.map { it.name }.toSet()
    private val livenessAtEndOfRoutine = when {
        routine.isEmpty() -> liveFromInputParameters(routine)
        else -> routine.liveness.getOut(routine.graph.endBlock)
    }

    override fun runAnalysis() {
        reportNotInitializedVariables()
        reportNotFinalizedVariables()
    }

    private fun reportNotInitializedVariables() {
        val expectedToBeInitialized = outputVariableNames
        val notInitialized = variablesNotInitialized(expectedToBeInitialized)
        reportVariables(notInitialized, "initialized", "finalizer")
    }

    private fun reportNotFinalizedVariables() {
        val expectedToBeFinalized = allVariableNames - outputVariableNames
        val notFinalized = variablesNotFinalized(expectedToBeFinalized)
        reportVariables(notFinalized, "finalized", "initializer")
    }

    private fun reportVariables(variables: Iterable<Variable>, expectedState: String, cause: String) {
        for ((name, potentialCausePositions) in variables) {
            val message = "Variable $name is not $expectedState at the end of routine."

            if (potentialCausePositions.isEmpty()) {
                reportError(message).withPositionOf(name)
            } else {
                for (causePosition in potentialCausePositions.sorted()) {
                    reportError(message).withAdditionalInfo(
                        "A potential cause for this might be the $cause here:", causePosition
                    )
                }
            }
        }
    }

    data class Variable(val name: Syntax.Identifier, val definitions: Set<Range>)

    private fun variablesNotInitialized(variables: Set<Syntax.Identifier>) =
        variablesNotInState<LivenessState.Initialized>(variables) { it.finalizers }

    private fun variablesNotFinalized(variables: Set<Syntax.Identifier>) =
        variablesNotInState<LivenessState.Finalized>(variables) { it.initializers }

    private inline fun <reified ExpectedState : LivenessState> variablesNotInState(
        variableNames: Set<Syntax.Identifier>,
        extractor: (LivenessState) -> Set<Range>
    ): Iterable<Variable> {
        val results = mutableListOf<Variable>()
        for (name in variableNames) {
            val finalState = livenessAtEndOfRoutine[ResourcePath.ofIdentifier(name)]
            if (finalState !is ExpectedState)
                results.add(Variable(name, extractor(finalState)))
        }
        return results.sortedBy { it.definitions.minOrNull() }
    }

    private companion object {
        private fun liveFromInputParameters(routine: Syntax.RoutineDefinition): LivenessDescriptor {
            val finalized = LivenessState.Finalized(emptySet())
            val live = LivenessDescriptor(routine, finalized)
            for (parameter in routine.inputParameters) {
                live += ResourcePath.ofIdentifier(parameter.name) to parameter.range
            }
            return live
        }
    }
}
