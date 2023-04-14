package ara.analysis

import ara.syntax.Syntax
import ara.types.Type

typealias MutableTypeMap = MutableMap<Syntax.Identifier, Type.Variable>
typealias TypeMap = Map<Syntax.Identifier, Type>

class TypeDeclarationAnalysis(private val program: Syntax.Program) : Analysis<TypeMap>() {

    override fun runAnalysis(): Map<Syntax.Identifier, Type> {
        val typeDeclarations = program.declarations.filterIsInstance<Syntax.TypeDeclaration>()

        val declaredTypes = initializeTypeMap(typeDeclarations)
        proceedAnalysis {
            for (declaration in typeDeclarations) {
                computeDeclaredType(declaredTypes, declaration)
            }
        }
        return declaredTypes
    }

    private fun initializeTypeMap(
        declarations: List<Syntax.TypeDeclaration>
    ): MutableTypeMap {
        val declaredTypes = mutableMapOf<Syntax.Identifier, Type.Variable>()
        for (declaration in declarations) {
            if (declaredTypes.containsKey(declaration.name)) {
                reportError(declaration, "Type ${declaration.name} is declared multiple times.")
            } else {
                declaredTypes[declaration.name] = Type.Variable()
            }
        }
        return declaredTypes
    }

    private fun computeDeclaredType(
        typeMap: MutableTypeMap,
        declaration: Syntax.TypeDeclaration
    ) {
        val context = TypeComputation(typeMap, declaration.type)
        val type = context.runAnalysis()

        if (context.hasReportedErrors) {
            context.reportedErrors
                .forEach(::reportError)
        } else {
            val typeError = TypeComputation.unify(
                typeMap[declaration.name]!!,
                type
            )
            when (typeError) {
                null ->
                    Unit

                is TypeComputation.RecursiveType ->
                    reportError(declaration, "Unable to construct infinite type ${declaration.name}.")

                else ->
                    reportError(declaration, "Unable declare type ${declaration.name}.")
            }
        }
    }
}