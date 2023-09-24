package ara.syntax.extensions

import ara.Direction
import ara.syntax.Syntax
import ara.types.Type


fun Syntax.RoutineDefinition.isEmpty(): Boolean =
    this.body.isEmpty()


val Syntax.Program.routines: List<Syntax.RoutineDefinition>
    get() = this.definitions.filterIsInstance<Syntax.RoutineDefinition>()

val Syntax.Program.types: List<Syntax.TypeDefinition>
    get() = this.definitions.filterIsInstance<Syntax.TypeDefinition>()


fun Syntax.RoutineDefinition.lookupVariableType(variable: Syntax.Identifier): Type? =
    this.localEnvironment.getVariable(variable)

fun Syntax.Control.isEntryPoint(): Boolean =
    this.direction == Direction.BACKWARD

fun Syntax.Control.isExitPoint(): Boolean =
    this.direction == Direction.FORWARD
