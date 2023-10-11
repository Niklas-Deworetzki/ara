package ara.analysis

import ara.syntax.Syntax

/**
 * Analysis pass collecting the names of all defined routines.
 */
class RoutineDefinitionAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() = forEachRoutineIn(program) {
        if (program.environment.defineRoutine(routine)) {
            debug { "Defined routine ${routine.name}" }
        } else {
            reportError("Routine ${routine.name} was defined multiple times.")
                .withPositionOf(routine.name)
        }
    }
}