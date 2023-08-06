package ara.input.symbol

import java_cup.runtime.Symbol

/**
 * Non-Terminal symbol used for CUP compatibility.
 */
class NonTerminal(
    val name: String,
    id: Int,
    left: Int,
    right: Int,
    value: Any?
) : Symbol(id, left, right, value)
