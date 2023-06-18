package ara.analysis

import ara.syntax.Syntax
import ara.syntax.extensions.routines

class RoutineDefinitionAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        for (definition in program.routines) {

            if (program.environment.defineRoutine(definition)) {
                debug { "Defined routine ${definition.name}" }
            } else {
                reportError("Routine ${definition.name} declared multiple times.")
                    .withPositionOf(definition.name)
            }
        }
    }
}