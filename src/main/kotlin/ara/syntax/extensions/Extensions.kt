package ara.syntax.extensions

import ara.syntax.Syntax


fun Syntax.RoutineDefinition.isEmpty(): Boolean =
    this.body.isEmpty()


val Syntax.Program.routines: List<Syntax.RoutineDefinition>
    get() = this.definitions.filterIsInstance<Syntax.RoutineDefinition>()

val Syntax.Program.types: List<Syntax.TypeDefinition>
    get() = this.definitions.filterIsInstance<Syntax.TypeDefinition>()
