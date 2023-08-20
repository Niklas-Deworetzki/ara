package ara.input

import ara.analysis.Analysis
import ara.input.symbol.SymbolFactory
import ara.input.symbol.Token
import ara.position.InputSource
import ara.position.Range
import ara.reporting.Message
import ara.syntax.Syntax
import ara.utils.formatting.formatToHumanReadable
import ara.utils.intersects

class InputAnalysis(val source: InputSource) : Analysis<Syntax.Program>() {
    private companion object {
        fun syntaxError(message: String): Message =
            Message.error("Syntax Error", message)

        val UNRECOVERABLE_SYNTAX_ERRORS = Message.error(message = "Syntax errors have been detected.")

        val STRUCTURE_FOR_IDS: List<Pair<Set<Int>, String>> = listOf(
            setOf(Sym.TYPE) to "a type definition",
            setOf(Sym.ROUTINE) to "a routine definition",
            setOf(Sym.IDENTIFIER) to "an identifier",
            setOf(Sym.INTEGER) to "an integer",
            setOf(Sym.CURL_L) to "start of routine or structure body",
            setOf(Sym.CURL_R) to "end of routine or structure body",
            setOf(Sym.COLON) to "a type annotation",
            setOf(Sym.COMMA) to "another element separated by a comma",
            setOf(Sym.DOT) to "a member access",
            setOf(
                Sym.OPERATOR_ADD,
                Sym.OPERATOR_SUB,
                Sym.OPERATOR_XOR
            ) to "a modification operator",
            setOf(
                Sym.OPERATOR_MUL,
                Sym.OPERATOR_DIV,
                Sym.OPERATOR_MOD
            ) to "an arithmetic operator",
            setOf(
                Sym.OPERATOR_LST,
                Sym.OPERATOR_LSE,
                Sym.OPERATOR_GRT,
                Sym.OPERATOR_GRE,
                Sym.OPERATOR_EQU,
                Sym.OPERATOR_NEQ
            ) to "a comparison operator",
            setOf(Sym.CALL) to "call",
            setOf(Sym.UNCALL) to "uncall",
            setOf(Sym.EQ) to "=",
            setOf(Sym.ASSIGNMENT) to ":=",
            setOf(Sym.PAREN_L) to "(",
            setOf(Sym.PAREN_R) to ")",
            setOf(Sym.ARROW_L) to "<-",
            setOf(Sym.ARROW_R) to "->"
        )

        fun translateTokenIdsToExpectedStructures(tokenIds: Set<Int>): String? {
            val expectedStructures = mutableListOf<String>()
            for ((expectedIds, structure) in STRUCTURE_FOR_IDS) {
                if (expectedIds intersects tokenIds) {
                    expectedStructures.add(structure)
                }
            }
            return expectedStructures.formatToHumanReadable(normalSeparator = ",", lastSeparator = " or ")
        }
    }

    override fun runAnalysis(): Syntax.Program =
        source.open().use { reader ->
            val scanner = Scanner(reader)
            val parser = Parser(scanner, SymbolFactory)

            parser.source = source
            parser.reporter = ErrorMessageProducingReporter()

            try {
                val parseResult = when {
                    isDebugEnabled -> parser.debug_parse()
                    else -> parser.parse()
                }
                parseResult.value as Syntax.Program
            } catch (exception: Exception) {
                reportError(UNRECOVERABLE_SYNTAX_ERRORS)
                Syntax.Program(emptyList())
            }
        }

    inner class ErrorMessageProducingReporter : SyntaxErrorReporter {
        override fun reportSyntaxError(token: Token, expectedTokenIds: List<Int>) {
            val range = Range(source, token.left.toLong(), token.right.toLong())
            val message = generateErrorMessage(token, expectedTokenIds)
            reportError(message.withPosition(range))
        }

        private fun generateErrorMessage(token: Token, expectedTokenIds: List<Int>): Message = when {
            token.sym == Sym.UNKNOWN ->
                Message.error(message = token.value.toString())

            else -> {
                val expected = translateTokenIdsToExpectedStructures(expectedTokenIds.toSet())
                if (expected != null) {
                    syntaxError("Expected $expected.")
                } else {
                    syntaxError("Unexpected token ${token.name}.")
                }
            }
        }
    }
}