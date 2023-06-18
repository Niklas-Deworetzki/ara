package ara.analysis

import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessProblem
import ara.analysis.live.LivenessState
import ara.analysis.live.LivenessState.Companion.meet
import ara.position.Range
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.syntax.extensions.isEmpty


class LivenessAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val routines = program.definitions.filterIsInstance<Syntax.RoutineDefinition>()
        for (routine in routines) {
            routine.liveness = LivenessProblem(routine).solve()
            for (block in routine.graph) {
                println(routine.liveness.getOut(block))
            }
            includeAnalysis(RoutineLivenessAnalysis(routine))
        }
    }

    class RoutineLivenessAnalysis(val routine: Syntax.RoutineDefinition) : Analysis<Unit>() {

        override fun runAnalysis() {
            reportConflicts()
            proceedAnalysis {
                includeAnalysis(CleanAnalysis(routine))

                for (block in routine.graph) {
                    includeAnalysis(BlockLevelLivenessAnalysis())
                }
            }
        }

        private fun reportConflicts() {
            val conflicts = ConflictsFinder(routine).getConflicts()
            for (variable in conflicts.keys.sorted()) {
                val conflict = conflicts[variable]!!

                // TODO: Check if there is no Conflict in OUT of predecessors, indicating that this is the root cause.
                val message = reportError("Variable $variable has conflicting initializers and finalizers.")
                val definitions = listOf(
                    conflict.initializers.map { it to "initializer" },
                    conflict.finalizers.map { it to "finalizer" }
                ).flatten().sortedBy { it.first }

                for ((position, definition) in definitions) {
                    message.withAdditionalInfo("A potential cause might be the $definition here:", position)
                }
            }
        }
    }

    class BlockLevelLivenessAnalysis() : Analysis<Unit>() {
        override fun runAnalysis() {
            // TODO: Verify initialization and finalization of instructions in every block.
        }
    }

    class CleanAnalysis(val routine: Syntax.RoutineDefinition) : Analysis<Unit>() {
        private val liveAtEndOfRoutine = when {
            routine.isEmpty() -> routine.liveFromParameterList(routine.inputParameters)
            else -> routine.liveness.getOut(routine.graph.endBlock)
        }

        override fun runAnalysis() {
            val allVariables = routine.localEnvironment.variableNames.toSet()
            val outputVariables = routine.outputParameters.map { it.name }.toSet()

            for ((name, finalizers) in variablesNotInitialized(outputVariables)) {
                val message = "Variable $name is not initialized at the end of routine."
                if (finalizers.isNotEmpty()) {
                    for (finalizer in finalizers) {
                        reportError(message)
                            .withAdditionalInfo("A potential cause for this might be the finalizer here:", finalizer)
                    }
                } else {
                    reportError(message).withPositionOf(name)
                }
            }
            for ((name, initializers) in variablesNotFinalized(allVariables - outputVariables)) {
                val message = "Variable $name is not finalized at the end of routine."
                if (initializers.isNotEmpty()) {
                    for (initializer in initializers) {
                        reportError(message)
                            .withAdditionalInfo("A potential cause for this might be the initializer here:", initializer)
                    }
                } else {
                    reportError(message).withPositionOf(name)
                }
            }
        }

        private fun variablesNotInitialized(variables: Set<Syntax.Identifier>) =
            variablesNotInState<LivenessState.Initialized, LivenessState.Finalized>(variables) { it.finalizers }

        private fun variablesNotFinalized(variables: Set<Syntax.Identifier>) =
            variablesNotInState<LivenessState.Finalized, LivenessState.Initialized>(variables) { it.initializers }

        private inline fun <reified ExpectedState : LivenessState, reified InvalidState : LivenessState> variablesNotInState(
            variables: Set<Syntax.Identifier>,
            extractor: (InvalidState) -> Set<Range>
        ): Iterable<Pair<Syntax.Identifier, Set<Range>>> {
            val results = mutableListOf<Pair<Syntax.Identifier, Set<Range>>>()
            for (variable in variables) {
                val finalState = liveAtEndOfRoutine[ResourcePath.ofIdentifier(variable)]
                if (finalState !is ExpectedState)
                    results.add(variable to extractor(finalState as InvalidState))
            }
            return results.sortedBy { it.second.minOrNull() }
        }
    }

    class ConflictsFinder(val routine: Syntax.RoutineDefinition) {
        private val detectedConflicts = mutableMapOf<Syntax.Identifier, LivenessState>()

        private fun initState() {
            for (variable in routine.localEnvironment.variableNames) {
                detectedConflicts[variable] = LivenessState.Unknown
            }
        }

        private fun updateConflict(
            variable: Syntax.Identifier,
            conflict: LivenessState.Conflict
        ) {
            detectedConflicts[variable] = detectedConflicts[variable]!! meet conflict
        }

        private fun detectConflicts() {
            for (block in routine.graph) {
                val liveAtEnd = routine.liveness.getOut(block)

                for (variable in routine.localEnvironment.variableNames) {
                    val path = ResourcePath.ofIdentifier(variable)
                    val potentialConflict = liveAtEnd[path]

                    if (potentialConflict is LivenessState.Conflict) {
                        updateConflict(variable, potentialConflict)
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun extractConflictsFromState(): Map<Syntax.Identifier, LivenessState.Conflict> {
            return detectedConflicts.filterValues { it is LivenessState.Conflict }
                    as Map<Syntax.Identifier, LivenessState.Conflict>
        }

        fun getConflicts(): Map<Syntax.Identifier, LivenessState.Conflict> {
            initState()
            detectConflicts()
            return extractConflictsFromState()
        }
    }

    companion object {
        fun Syntax.RoutineDefinition.liveFromParameterList(parameters: List<Syntax.Parameter>): LivenessDescriptor {
            val finalized = LivenessState.Finalized(emptySet())
            val live = LivenessDescriptor(this, finalized)
            for (parameter in parameters) {
                live += ResourcePath.ofIdentifier(parameter.name) to parameter.range
            }
            return live
        }
    }
}
