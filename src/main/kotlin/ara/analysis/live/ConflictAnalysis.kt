package ara.analysis.live

import ara.analysis.Analysis
import ara.analysis.live.LivenessState.Companion.meet
import ara.control.Block
import ara.position.Range
import ara.storage.ResourcePath
import ara.syntax.Syntax

class ConflictAnalysis(val routine: Syntax.RoutineDefinition) : Analysis<Unit>() {
    private val detectedConflicts = mutableMapOf<Syntax.Identifier, LivenessState>()

    init {
        initState()
        detectConflicts()
    }

    override fun runAnalysis() {
        for (conflict in getConflicts().sortedBy { it.variable.name }) {
            val message = reportError("Variable ${conflict.variable} has conflicting initializers and finalizers.")
            for (definition in conflict.definitions.sortedBy { it.range }) {
                message.withAdditionalInfo(
                    "Potential causes include the ${definition.description} here:", definition.range
                )
            }
        }
    }

    private fun getConflicts(): List<Conflict> {
        val conflicts = extractConflictsFromState()
        return conflicts.map { (variable, conflict) ->
            val definitions = extractDefinitionsFromConflict(conflict)
            Conflict(variable, definitions)
        }
    }

    private fun extractDefinitionsFromConflict(conflict: LivenessState.Conflict): List<Definition> =
        conflict.initializers.map { Definition(it, "initializer") } +
                conflict.finalizers.map { Definition(it, "finalizer") }

    data class Conflict(val variable: Syntax.Identifier, val definitions: List<Definition>)

    data class Definition(val range: Range, val description: String)

    private fun initState() {
        for (variable in routine.localEnvironment.variableNames) {
            detectedConflicts[variable] = LivenessState.Unknown
        }
    }

    private fun detectConflicts() {
        for (block in routine.graph) {
            val livenessAtEndOfBlock = routine.liveness.getOut(block)

            for (variable in routine.localEnvironment.variableNames) {
                val path = ResourcePath.ofIdentifier(variable)
                val potentialConflict = livenessAtEndOfBlock[path]

                if (potentialConflict is LivenessState.Conflict &&
                    !variableIsConflictInPredecessors(block, path)
                ) {
                    // Narrow down causes to the first block in which a variable becomes Conflict.
                    updateConflict(variable, potentialConflict)
                }
            }
        }
    }

    private fun updateConflict(variable: Syntax.Identifier, conflict: LivenessState.Conflict) {
        detectedConflicts[variable] = detectedConflicts[variable]!! meet conflict
    }

    private fun variableIsConflictInPredecessors(block: Block, variable: ResourcePath): Boolean {
        for (predecessor in routine.graph.getPredecessors(block)) {
            val variableStateAtEndOfPredecessor = routine.liveness.getOut(predecessor)[variable]
            if (variableStateAtEndOfPredecessor is LivenessState.Conflict)
                return true
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractConflictsFromState(): Map<Syntax.Identifier, LivenessState.Conflict> {
        return detectedConflicts.filterValues { it is LivenessState.Conflict }
                as Map<Syntax.Identifier, LivenessState.Conflict>
    }
}
