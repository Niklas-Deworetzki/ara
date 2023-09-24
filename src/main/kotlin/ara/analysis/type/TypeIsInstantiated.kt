package ara.analysis.type

import ara.types.Type
import ara.utils.NonEmptyList

object TypeIsInstantiated : Type.Algebra<Boolean> {
    override fun builtin(builtin: Type.Builtin): Boolean = true

    override fun reference(base: Boolean): Boolean = base

    override fun structure(memberNames: NonEmptyList<String>, memberValues: NonEmptyList<Boolean>): Boolean =
        memberValues.all { it }

    override fun uninitializedVariable(): Boolean = false


    fun Type.isInstantiated(): Boolean = TypeIsInstantiated.evaluate(this)
}
