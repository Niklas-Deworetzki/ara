package ara.input

import ara.position.InputSource
import io.kotest.matchers.shouldBe
import java_cup.runtime.Symbol
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ScannerTest {

    @Test
    fun scannerReturnsEOF() {
        val token = "".firstToken()

        token.shouldBeToken(Sym.EOF)
    }

    @Test
    fun scannerRecognizesSimpleTokens() {
        "routine".firstToken().shouldBeToken(Sym.ROUTINE)
        "type".firstToken().shouldBeToken(Sym.TYPE)
        "call".firstToken().shouldBeToken(Sym.CALL)
        "uncall".firstToken().shouldBeToken(Sym.UNCALL)
        "null".firstToken().shouldBeToken(Sym.NULL)
        ":".firstToken().shouldBeToken(Sym.COLON)
        ",".firstToken().shouldBeToken(Sym.COMMA)
        ".".firstToken().shouldBeToken(Sym.DOT)
        ":=".firstToken().shouldBeToken(Sym.ASSIGNMENT)
        "+".firstToken().shouldBeToken(Sym.OPERATOR_ADD)
        "-".firstToken().shouldBeToken(Sym.OPERATOR_SUB)
        "^".firstToken().shouldBeToken(Sym.OPERATOR_XOR)
        "*".firstToken().shouldBeToken(Sym.OPERATOR_MUL)
        "/".firstToken().shouldBeToken(Sym.OPERATOR_DIV)
        "%".firstToken().shouldBeToken(Sym.OPERATOR_MOD)
        "==".firstToken().shouldBeToken(Sym.OPERATOR_EQU)
        "!=".firstToken().shouldBeToken(Sym.OPERATOR_NEQ)
        "<".firstToken().shouldBeToken(Sym.OPERATOR_LST)
        "<=".firstToken().shouldBeToken(Sym.OPERATOR_LSE)
        ">".firstToken().shouldBeToken(Sym.OPERATOR_GRT)
        ">=".firstToken().shouldBeToken(Sym.OPERATOR_GRE)
        "<-".firstToken().shouldBeToken(Sym.ARROW_L)
        "->".firstToken().shouldBeToken(Sym.ARROW_R)
        "(".firstToken().shouldBeToken(Sym.PAREN_L)
        ")".firstToken().shouldBeToken(Sym.PAREN_R)
        "{".firstToken().shouldBeToken(Sym.CURL_L)
        "}".firstToken().shouldBeToken(Sym.CURL_R)
        "&".firstToken().shouldBeToken(Sym.AMPERSAND)
        "&(".firstToken().shouldBeToken(Sym.AMPERSAND_PAREN_L)
        "&{".firstToken().shouldBeToken(Sym.AMPERSAND_CURL_L)
    }

    @Test
    fun scannerSkipsWhitespace() {
        " \t\n\r+".firstToken().shouldBeToken(Sym.OPERATOR_ADD)
    }

    @Test
    fun scannerSkipsComments() {
        val tokens = """
            // This should be recognized as a comment.
            + # This should also be a comment.
        """.tokens()

        tokens.size.shouldBe(1)
        tokens.first().shouldBeToken(Sym.OPERATOR_ADD)
    }

    @Test
    fun scannerExtractsTextFromHashComments() {
        val token = "#I am a special comment.".firstToken()

        token.shouldBeToken(Sym.HASHCOMMENT)
        token.value.shouldBe(listOf("I am a special comment."))
    }

    @Test
    fun scannerExtractTextFromMultipleHashComments() {
        val token = """
            # a
            # b
            #
            # c
        """.trimIndent().firstToken()

        token.value.shouldBe(listOf(" a", " b", "", " c"))
    }

    @Test
    fun scannerRecognizesIdentifiers() {
        val token = "aB_0".firstToken()
        token.shouldBeToken(Sym.IDENTIFIER)
        token.value.shouldBe("aB_0")
    }

    @Test
    fun scannerRecognizesIntegers() {
        val token = "0123456789".firstToken()
        token.shouldBeToken(Sym.INTEGER)
        token.value.shouldBe(123456789)
    }

    @Test
    fun scannerRecognizesSequence() {
        """
            identifier_ending_with_numbers123456789
            AAAA bbbb A_a routine type call uncall null
            0 1 2 3 4 5 6 7 8 9
            // All operators:
            + - ^ * / % == != < <= > >=
            // Arrows and remaining stuff
            <- -> ( ) { } : , . := = & &( &{
        """.shouldContainTokensAndEOF(
            Sym.IDENTIFIER,
            Sym.IDENTIFIER,
            Sym.IDENTIFIER,
            Sym.IDENTIFIER,
            Sym.ROUTINE,
            Sym.TYPE,
            Sym.CALL,
            Sym.UNCALL,
            Sym.NULL,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.INTEGER,
            Sym.OPERATOR_ADD,
            Sym.OPERATOR_SUB,
            Sym.OPERATOR_XOR,
            Sym.OPERATOR_MUL,
            Sym.OPERATOR_DIV,
            Sym.OPERATOR_MOD,
            Sym.OPERATOR_EQU,
            Sym.OPERATOR_NEQ,
            Sym.OPERATOR_LST,
            Sym.OPERATOR_LSE,
            Sym.OPERATOR_GRT,
            Sym.OPERATOR_GRE,
            Sym.ARROW_L,
            Sym.ARROW_R,
            Sym.PAREN_L,
            Sym.PAREN_R,
            Sym.CURL_L,
            Sym.CURL_R,
            Sym.COLON,
            Sym.COMMA,
            Sym.DOT,
            Sym.ASSIGNMENT,
            Sym.EQ,
            Sym.AMPERSAND,
            Sym.AMPERSAND_PAREN_L,
            Sym.AMPERSAND_CURL_L
        )
    }

    @Test
    fun scannerRecognizesIncompleteTokens() {
        "!".shouldContainTokensAndEOF(Sym.UNKNOWN)
    }

    @Test
    fun scannerRecognizesIncompleteRoutineKeywordAsIdentifier() {
        val keywords = listOf("routine", "type", "call", "uncall")

        for (keyword in keywords) {
            for (length in 1 until keyword.length - 1) {
                keyword.substring(0, length).firstToken()
                    .shouldBeToken(Sym.IDENTIFIER)
            }
        }
    }

    private fun String.shouldContainTokensAndEOF(vararg types: Int) {
        val input = InputSource.fromString(this)
        val typesIterator = types.iterator()

        input.open().use {
            val scanner = Scanner(it)
            while (typesIterator.hasNext()) {
                val scannedToken = scanner.next_token()
                val expectedTokenType = typesIterator.next()

                scannedToken.shouldBeToken(expectedTokenType)
            }

            val nextToken = scanner.next_token()
            nextToken.shouldBeToken(Sym.EOF)
        }
    }

    private fun String.firstToken(): Symbol {
        val input = InputSource.fromString(this)
        return input.open().use { Scanner(it).next_token() }
    }

    private fun String.tokens(): List<Symbol> {
        val input = InputSource.fromString(this)
        return input.open().use {
            val scanner = Scanner(it)
            val tokens = mutableListOf<Symbol>()
            var currentToken: Symbol

            do {
                currentToken = scanner.next_token()
                tokens.add(currentToken)
            } while (currentToken.sym != Sym.EOF)
            tokens.dropLast(1)
        }
    }

    private fun Symbol.shouldBeToken(id: Int) {
        if (this.sym != id) {
            val expectedId = Sym.terminalNames.getOrElse(id) {
                throw AssertionError("Expected id is not a valid token id.")
            }
            val actualId = Sym.terminalNames.getOrNull(this.sym)

            if (actualId == null) {
                fail("Not a valid symbol: $this")
            } else {
                fail("Expected $expectedId but got $actualId.")
            }
        }
    }
}