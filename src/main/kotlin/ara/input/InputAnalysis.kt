package ara.input

import ara.analysis.Analysis
import ara.input.symbol.SymbolFactory
import ara.input.symbol.Token
import ara.position.InputSource
import ara.position.Range
import ara.reporting.Message
import ara.syntax.Syntax

class InputAnalysis(val source: InputSource) : Analysis<Syntax.Program>() {

    override fun runAnalysis(): Syntax.Program =
        source.open().use { reader ->
            val scanner = Scanner(reader)
            val parser = Parser(scanner, SymbolFactory)

            parser.source = source
            parser.reporter = ErrorMessageProducingReporter()

            try {
                parser.parse().value as Syntax.Program
            } catch (exception: Exception) {
                reportError(UNRECOVERABLE_SYNTAX_ERRORS)
                Syntax.Program(emptyList())
            }
        }

    inner class ErrorMessageProducingReporter : SyntaxErrorReporter {
        override fun reportSyntaxError(token: Token, expectedTokenIds: MutableList<Int>) {
            when {
                token.sym == Sym.UNKNOWN ->
                    reportError(
                        Message.error(message = token.value.toString())
                    )

                else -> {
                    val tokenTypes = expectedTokenIds.map { translateTokenIdsToName(it) }
                        .toSet()

                    val message = syntaxError("Unexpected ${token.name}, expected $tokenTypes")
                        .withPosition(Range(source, token.left.toLong(), token.right.toLong()))
                    reportError(message)
                }
            }
        }
    }

    private companion object {
        fun syntaxError(message: String): Message =
            Message.error("Syntax Error", message)

        val UNRECOVERABLE_SYNTAX_ERRORS = Message.error(message = "Syntax errors have been detected.")

        fun translateTokenIdsToName(id: Int): String =
            Sym.terminalNames[id]
    }
}