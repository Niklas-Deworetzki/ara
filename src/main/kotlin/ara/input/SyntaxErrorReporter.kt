package ara.input

import ara.input.symbol.Token
import ara.position.Range

interface SyntaxErrorReporter {
    fun reportWrongToken(token: Token, expectedTokenIds: List<Int>)

    fun reportCustomError(message: String, range: Range)
}
