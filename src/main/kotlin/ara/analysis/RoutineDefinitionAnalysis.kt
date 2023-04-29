package ara.analysis

import ara.syntax.Syntax

class RoutineDefinitionAnalysis(private val program: Syntax.Program) : Analysis<Unit>() {

    override fun runAnalysis() {
        for (definition in program.definitions
            .filterIsInstance<Syntax.RoutineDefinition>()) {

            if (!program.environment.defineRoutine(definition)) {
                reportError(definition.name, "Routine ${definition.name} declared multiple times.")
            }
        }
    }
}