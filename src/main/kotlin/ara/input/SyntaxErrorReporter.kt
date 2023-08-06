package ara.input

import ara.input.symbol.Token

fun interface SyntaxErrorReporter {
    fun reportSyntaxError(token: Token, expectedTokenIds: List<Int>)
}
