package ara.analysis.type

import ara.types.Type
import ara.types.Type.Algebra.Companion.evaluate

object TypeIsInstantiated : Type.Algebra<Boolean> {
    override fun builtin(builtin: Type.BuiltinType): Boolean = true

    override fun structure(memberNames: List<String>, memberValues: List<Boolean>): Boolean =
        memberValues.all { it }

    override fun uninitializedVariable(): Boolean = false


    fun Type.isInstantiated(): Boolean = TypeIsInstantiated.evaluate(this)
}
