package ara.analysis

import ara.analysis.dataflow.DataflowSolver.Companion.solve
import ara.analysis.live.LivenessDescriptor
import ara.analysis.live.LivenessProblem
import ara.analysis.live.LivenessState
import ara.analysis.live.LivenessState.Companion.meet
import ara.control.Block
import ara.position.Range
import ara.reporting.Message.Companion.quoted
import ara.storage.ResourceAllocation.asResourcePath
import ara.storage.ResourceAllocation.asResourcePaths
import ara.storage.ResourceAllocation.resourcesCreated
import ara.storage.ResourceAllocation.resourcesDestroyed
import ara.storage.ResourcePath
import ara.syntax.Syntax
import ara.syntax.extensions.isEmpty


class LivenessAnalysis(val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        val routines = program.definitions.filterIsInstance<Syntax.RoutineDefinition>()
        for (routine in routines) {
            routine.liveness = LivenessProblem(routine).solve()
            includeAnalysis(RoutineLivenessAnalysis(routine))
        }
    }

    class RoutineLivenessAnalysis(val routine: Syntax.RoutineDefinition) : Analysis<Unit>() {

        override fun runAnalysis() {
            reportConflicts()
            proceedAnalysis {
                includeAnalysis(CleanAnalysis(routine))

                for (block in routine.graph) {
                    includeAnalysis(BlockLevelLivenessAnalysis(routine, block))
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

    class BlockLevelLivenessAnalysis(private val routine: Syntax.RoutineDefinition, private val block: Block) :
        Analysis<Unit>() {
        private val currentState = routine.liveness.getIn(block)

        override fun runAnalysis() {
            for (instruction in block) {
                instruction.resourcesDestroyed().forEach(::finalizeOrReport)
                verifyUses(instruction)
                instruction.resourcesCreated().forEach(::initializeOrReport)
            }
        }

        private fun initializeOrReport(expression: Syntax.ResourceExpression) {
            val resource = expression.asResourcePath()
            if (resource != null) {
                val state = currentState[resource]
                if (state !is LivenessState.Finalized) {
                    val error =
                        reportError("Cannot initialize ${resource.quoted()} as it has already been initialized.")
                            .withPositionOf(expression)

                    for (initializer in state.initializers) {
                        error.withAdditionalInfo("Potential causes include the initializer defined here:", initializer)
                    }

                } else {
                    currentState += resource to expression.range
                }
            }
        }

        private fun finalizeOrReport(expression: Syntax.ResourceExpression) {
            val resource = expression.asResourcePath()
            if (resource != null) {
                val state = currentState[resource]
                if (state !is LivenessState.Initialized) {
                    val error =
                        reportError("Cannot finalize ${resource.quoted()} as it has already been finalized.")
                            .withPositionOf(expression)

                    for (initializer in state.initializers) {
                        error.withAdditionalInfo("Potential causes include the finalizer defined here:", initializer)
                    }

                } else {
                    currentState -= resource to expression.range
                }
            }
        }

        private fun verifyUse(resource: ResourcePath, anchor: Syntax) {
            val state = currentState[resource]
            if (state !is LivenessState.Initialized) {
                val error = reportError("Cannot use ${resource.quoted()} as it has not been initialized.")
                    .withPositionOf(anchor)

                for (finalizer in state.finalizers) {
                    error.withAdditionalInfo("This is potentially caused by the finalizer defined here:", finalizer)
                }
            }
        }

        private fun verifyUses(instruction: Syntax.Instruction) {
            when (instruction) {
                is Syntax.Assignment -> {
                    if (instruction.arithmetic != null) {
                        val resources = instruction.arithmetic.value.asResourcePaths().toSet()
                        for (resource in resources) {
                            verifyUse(resource, instruction.arithmetic)
                        }
                    }
                }

                is Syntax.Conditional -> {
                    val resources = instruction.condition.asResourcePaths().toSet()
                    for (resource in resources) {
                        verifyUse(resource, instruction.condition)
                    }
                }

                else ->
                    Unit
            }
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

            val notInitialized = variablesNotInitialized(outputVariables)
            reportVariables(notInitialized, "initialized", "finalizer")
            val notFinalized = variablesNotFinalized(allVariables - outputVariables)
            reportVariables(notFinalized, "finalized", "initializer")
        }

        private fun reportVariables(
            variables: Iterable<Pair<Syntax.Identifier, Set<Range>>>,
            expectedState: String,
            cause: String
        ) {
            for ((name, potentialCausePositions) in variables) {
                val message = "Variable $name is not $expectedState at the end of routine."

                if (potentialCausePositions.isNotEmpty()) {
                    for (potentialCausePosition in potentialCausePositions) {
                        reportError(message)
                            .withAdditionalInfo(
                                "A potential cause for this might be the $cause here:",
                                potentialCausePosition
                            )
                    }
                } else {
                    reportError(message).withPositionOf(name)
                }
            }
        }

        private fun variablesNotInitialized(variables: Set<Syntax.Identifier>) =
            variablesNotInState<LivenessState.Initialized>(variables) { it.finalizers }

        private fun variablesNotFinalized(variables: Set<Syntax.Identifier>) =
            variablesNotInState<LivenessState.Finalized>(variables) { it.initializers }

        private inline fun <reified ExpectedState : LivenessState> variablesNotInState(
            variables: Set<Syntax.Identifier>,
            extractor: (LivenessState) -> Set<Range>
        ): Iterable<Pair<Syntax.Identifier, Set<Range>>> {
            val results = mutableListOf<Pair<Syntax.Identifier, Set<Range>>>()
            for (variable in variables) {
                val finalState = liveAtEndOfRoutine[ResourcePath.ofIdentifier(variable)]
                if (finalState !is ExpectedState)
                    results.add(variable to extractor(finalState))
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
