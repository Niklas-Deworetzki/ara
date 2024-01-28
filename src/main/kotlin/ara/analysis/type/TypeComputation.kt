package ara.analysis.type

import ara.analysis.Analysis
import ara.syntax.Syntax
import ara.types.Environment
import ara.types.Type

class TypeComputation(
    private val environment: Environment,
    private val typeExpression: Syntax.Type
) : Analysis<Type>() {

    override fun runAnalysis(): Type =
        typeExpression.asType()

    private fun Syntax.Type.asType(): Type {
        when (this) {
            is Syntax.NamedType -> {
                val type = environment.getType(name) ?: return reportUnknownType(name)
                return Type.ResolvedName(name.name, type)
            }

            is Syntax.ReferenceType ->
                return Type.Reference(baseType.asType())

            is Syntax.StructureType -> {
                val members = this.members.map {
                    Type.Member(it.name.name, it.type.asType())
                }
                return Type.fromMembers(members)
            }
        }
    }

    private fun reportUnknownType(name: Syntax.Identifier): Type.Variable {
        reportError("Unknown type $name.")
            .withPositionOf(name)
        return Type.Variable()
    }

    companion object {
        fun Analysis<*>.computedType(type: Syntax.Type, environment: Environment): Type =
            includeAnalysis(TypeComputation(environment, type))
    }
}
