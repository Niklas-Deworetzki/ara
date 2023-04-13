package ara.analysis

import ara.syntax.Syntax

typealias RoutineMap = Map<Syntax.Identifier, Syntax.RoutineDeclaration>
typealias MutableRoutineMap = MutableMap<Syntax.Identifier, Syntax.RoutineDeclaration>

class RoutineDeclarationAnalysis(private val program: Syntax.Program) : Analysis<RoutineMap>() {

    override fun runAnalysis(): RoutineMap {
        val declaredRoutines: MutableRoutineMap = mutableMapOf()
        for (declaration in program.declarations
            .filterIsInstance<Syntax.RoutineDeclaration>()) {

            if (declaredRoutines.containsKey(declaration.name)) {
                reportError(declaration.name, "Routine ${declaration.name} declared multiple times.")
            } else {
                declaredRoutines[declaration.name] = declaration
            }
        }
        return declaredRoutines
    }
}