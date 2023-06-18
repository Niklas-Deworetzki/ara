package ara.analysis

import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessProblem
import ara.analysis.live.LivenessState
import ara.analysis.live.LivenessState.Companion.meet
import ara.storage.ResourcePath
import ara.syntax.Syntax


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
                reportNotInitializedVariables()
                reportNotFinalizedVariables()
            }
            // TODO: Verify initialization and finalization in every block.
        }

        private fun reportNotFinalizedVariables() {
            routine.localEnvironment.variables

        }

        private fun reportNotInitializedVariables() {

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
                    message.withAdditionalInfo(position, "A potential cause might be the $definition here:")
                }
            }
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
