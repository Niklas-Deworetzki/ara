package ara.types

object TypeUnification {

    fun unify(a: Type, b: Type): Error? {
        if (a == b) return null

        if (a is Type.Variable) {
            if (a.type != null) {
                return unify(a.type!!, b)

            } else if (a in b) {
                return Error.RecursiveType

            } else {
                a.type = b
            }
            return null

        } else if (b is Type.Variable) {
            return unify(b, a)

        } else if (a is Type.Structure && b is Type.Structure) {
            if (a.members.size != b.members.size)
                return Error.DifferentStructSize(a.members, b.members)

            val maxIndex = a.members.size
            for (i in 0 until maxIndex) {
                val aMember = a.members[i]
                val bMember = b.members[i]

                if (aMember.name != bMember.name)
                    return Error.DifferentMemberNames(i, aMember, bMember)

                val recursiveResult = unify(aMember.type, bMember.type)
                if (recursiveResult != null)
                    return Error.DifferentMemberTypes(i, aMember, bMember, recursiveResult)
            }
            return null

        } else {
            return Error.NotUnifiable(a, b)
        }
    }


    sealed interface Error {
        object RecursiveType : Error

        data class NotUnifiable(
            val a: Type,
            val b: Type
        ) : Error

        data class DifferentStructSize(
            val aMembers: List<Type.Member>,
            val bMembers: List<Type.Member>
        ) : Error

        data class DifferentMemberNames(
            val index: Int,
            val aMember: Type.Member,
            val bMember: Type.Member
        ) : Error

        data class DifferentMemberTypes(
            val index: Int,
            val aMember: Type.Member,
            val bMember: Type.Member,
            val cause: Error
        ) : Error

        fun unfold(): List<Error> {
            val errorTrace = mutableListOf<Error>()
            var cause: Error? = this

            while (cause != null) {
                errorTrace.add(cause)
                cause = when (cause) {
                    is DifferentMemberTypes ->
                        cause.cause

                    RecursiveType, is NotUnifiable, is DifferentMemberNames, is DifferentStructSize ->
                        null
                }
            }
            return errorTrace
        }
    }
}