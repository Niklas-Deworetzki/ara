package ara.analysis

import ara.syntax.Syntax
import ara.types.Type

class TypeComputation(
    private val declaredTypes: Map<Syntax.Identifier, Type>,
    private val typeExpression: Syntax.Type
) : Analysis<Type>() {

    override fun runAnalysis(): Type =
        typeExpression.asType()

    private fun Syntax.Type.asType(): Type = when (this) {
        is Syntax.NamedType ->
            lookupType(this.name)

        is Syntax.ReferenceType ->
            Type.Reference(this.baseType.asType())

        is Syntax.StructureType -> {
            val members = this.members.map {
                Type.Member(it.name.name, it.type.asType())
            }
            Type.Structure(members)
        }
    }

    private fun lookupType(name: Syntax.Identifier): Type =
        declaredTypes.getOrElse(name) {
            reportError(name, "Unknown type $name.")
            Type.Variable()
        }

    companion object {

        fun Type.normalize(): Type = when (this) {
            is Type.BuiltinType ->
                this

            is Type.Variable ->
                this.type?.normalize()
                    ?: throw IllegalStateException("Cannot normalize uninstantiated type variable!")

            is Type.Reference -> {
                val normalizedBase = this.base.normalize()
                Type.Reference(normalizedBase)
            }

            is Type.Structure -> {
                val normalizedMembers = this.members.map {
                    Type.Member(it.name, it.type.normalize())
                }
                Type.Structure(normalizedMembers)
            }
        }

        tailrec fun Type.getMemberType(name: String): Type? = when (this) {
            is Type.Variable ->
                this.type?.getMemberType(name)

            is Type.Structure ->
                members.firstOrNull { it.name == name }?.type

            else ->
                null
        }

        fun Type.unpackReference(): Type? = when (this) {
            is Type.Variable ->
                this.type?.unpackReference()

            is Type.Reference ->
                this.base

            else ->
                null
        }

        fun unify(a: Type, b: Type): TypeError? {
            if (a == b) return null

            if (a is Type.Variable) {
                if (a.type != null) {
                    return unify(a.type!!, b)

                } else if (a in b) {
                    return RecursiveType

                } else {
                    a.type = b
                }
                return null

            } else if (b is Type.Variable) {
                return unify(b, a)

            } else if (a is Type.Reference && b is Type.Reference) {
                return unify(a.base, b.base)

            } else if (a is Type.Structure && b is Type.Structure) {
                if (a.members.size != b.members.size)
                    return DifferentStructSize(a.members, b.members)

                val maxIndex = a.members.size
                for (i in 0..maxIndex) {
                    val aMember = a.members[i]
                    val bMember = b.members[i]

                    if (aMember.name != bMember.name)
                        return DifferentMemberNames(i, aMember, bMember)

                    val recursiveResult = unify(aMember.type, bMember.type)
                    if (recursiveResult != null)
                        return DifferentMemberTypes(i, aMember, bMember, recursiveResult)
                }
                return null

            } else {
                return NotUnifiable(a, b)
            }
        }
    }

    sealed interface TypeError
    object RecursiveType : TypeError

    data class NotUnifiable(
        val a: Type,
        val b: Type
    ) : TypeError

    data class DifferentStructSize(
        val aMembers: List<Type.Member>,
        val bMembers: List<Type.Member>
    ) : TypeError

    data class DifferentMemberNames(
        val index: Int,
        val aMember: Type.Member,
        val bMember: Type.Member
    ) : TypeError

    data class DifferentMemberTypes(
        val index: Int,
        val aMember: Type.Member,
        val bMember: Type.Member,
        val cause: TypeError
    ) : TypeError
}
