package ara.input.symbol

import ara.input.Sym
import java_cup.runtime.Symbol

/**
 * Terminal symbol used for JFlex & CUP compatibility.
 */
class Token(
    id: Int,
    left: Int,
    right: Int,
    value: Any?
) : Symbol(id, left, right, value) {

    val name: String
        get() = Sym.terminalNames[sym]

    companion object {
        @JvmStatic
        @JvmOverloads
        fun withId(tokenId: Int, left: Int, right: Int, value: Any? = null): Token =
            Token(tokenId, left, right, value)
    }

    override fun toString(): String = when {
        value != null -> "$name($value)"
        else -> name
    }
}