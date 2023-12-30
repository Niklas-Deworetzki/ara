package ara.storage.extensions

import ara.syntax.Syntax

internal object ForResources {

    @JvmStatic
    fun resourcesCreated(instruction: Syntax.Instruction): Collection<Syntax.ResourceExpression> = when (instruction) {
        is Syntax.ArithmeticAssignment ->
            listOf(instruction.dst)

        is Syntax.MultiAssignment ->
            instruction.dstList

        is Syntax.Call ->
            instruction.dstList

        is Syntax.Conditional, is Syntax.Unconditional ->
            emptyList()
    }

    @JvmStatic
    fun resourcesDestroyed(instruction: Syntax.Instruction): Collection<Syntax.ResourceExpression> = when (instruction) {
        is Syntax.ArithmeticAssignment ->
            listOf(instruction.src)

        is Syntax.MultiAssignment ->
            instruction.srcList

        is Syntax.Call ->
            instruction.srcList

        is Syntax.Conditional, is Syntax.Unconditional ->
            emptyList()
    }
}
