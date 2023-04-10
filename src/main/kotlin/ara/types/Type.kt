package ara.types

sealed class Type {

    data class Variable(
        var type: Type? = null
    ) : Type()


    object Integer : Type()

    object Boolean : Type()

    data class Reference(
        val base: Type
    ) : Type()

    data class Structure(
        val members: List<Member>
    ) : Type()

    data class Member(
        val name: String,
        val type: Type
    )
}
