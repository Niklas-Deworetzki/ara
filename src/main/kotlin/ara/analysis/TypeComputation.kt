package ara.analysis

import ara.syntax.Syntax
import ara.types.Environment
import ara.types.Type

class TypeComputation(
    private val environment: Environment,
    private val typeExpression: Syntax.Type
) : Analysis<Type>() {

    override fun runAnalysis(): Type =
        typeExpression.asType()

    private fun Syntax.Type.asType(): Type = when (this) {
        is Syntax.NamedType ->
            lookupType(this.name)

        is Syntax.StructureType -> {
            val members = this.members.map {
                Type.Member(it.name.name, it.type.asType())
            }
            Type.Structure(members)
        }
    }

    private fun lookupType(name: Syntax.Identifier): Type =
        environment.getType(name) ?: reportUnknownType(name)

    private fun reportUnknownType(name: Syntax.Identifier): Type.Variable {
        reportError(name, "Unknown type $name.")
        return Type.Variable()
    }
}
