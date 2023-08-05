package ara.input.symbol

import java_cup.runtime.Symbol
import java_cup.runtime.SymbolFactory

/**
 * Simple [SymbolFactory] implementation required for CUP.
 */
object SymbolFactory : SymbolFactory {

    override fun startSymbol(name: String, id: Int, state: Int): Symbol {
        val startSymbol = NonTerminal(name, id, -1, -1, null)
        startSymbol.parse_state = state
        return startSymbol
    }

    override fun newSymbol(name: String, id: Int, left: Symbol?, right: Symbol?, value: Any?): Symbol {
        val lleft = left?.left ?: -1
        val rright = right?.right ?: -1
        return NonTerminal(name, id, lleft, rright, value)
    }

    override fun newSymbol(name: String, id: Int, left: Symbol?, value: Any?): Symbol {
        return newSymbol(name, id, left, left, value)
    }

    override fun newSymbol(name: String, id: Int, left: Symbol?, right: Symbol?): Symbol {
        return newSymbol(name, id, left, right, null)
    }

    override fun newSymbol(name: String, id: Int): Symbol {
        return newSymbol(name, id, null)
    }

    override fun newSymbol(name: String, id: Int, value: Any?): Symbol {
        return NonTerminal(name, id, -1, -1, value)
    }
}