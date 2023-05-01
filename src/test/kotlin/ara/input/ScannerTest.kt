package ara.input

import ara.position.InputSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScannerTest {

    @Test
    fun scannerReturnsEOF() {
        val token = "".firstToken()

        token.type.shouldBe(Token.Type.EOF)
    }

    @Test
    fun scannerRecognizesSimpleTokens() {
        "routine".firstToken().type.shouldBe(Token.Type.ROUTINE)
        ":".firstToken().type.shouldBe(Token.Type.COLON)
        ",".firstToken().type.shouldBe(Token.Type.COMMA)
        ".".firstToken().type.shouldBe(Token.Type.DOT)
        ":=".firstToken().type.shouldBe(Token.Type.ASSIGNMENT)
        "+".firstToken().type.shouldBe(Token.Type.OPERATOR_ADD)
        "-".firstToken().type.shouldBe(Token.Type.OPERATOR_SUB)
        "^".firstToken().type.shouldBe(Token.Type.OPERATOR_XOR)
        "*".firstToken().type.shouldBe(Token.Type.OPERATOR_MUL)
        "/".firstToken().type.shouldBe(Token.Type.OPERATOR_DIV)
        "%".firstToken().type.shouldBe(Token.Type.OPERATOR_MOD)
        "==".firstToken().type.shouldBe(Token.Type.OPERATOR_EQU)
        "!=".firstToken().type.shouldBe(Token.Type.OPERATOR_NEQ)
        "<".firstToken().type.shouldBe(Token.Type.OPERATOR_LST)
        "<=".firstToken().type.shouldBe(Token.Type.OPERATOR_LSE)
        ">".firstToken().type.shouldBe(Token.Type.OPERATOR_GRT)
        ">=".firstToken().type.shouldBe(Token.Type.OPERATOR_GRE)
        "<-".firstToken().type.shouldBe(Token.Type.ARROW_L)
        "->".firstToken().type.shouldBe(Token.Type.ARROW_R)
        "(".firstToken().type.shouldBe(Token.Type.PAREN_L)
        ")".firstToken().type.shouldBe(Token.Type.PAREN_R)
        "{".firstToken().type.shouldBe(Token.Type.CURL_L)
        "}".firstToken().type.shouldBe(Token.Type.CURL_R)
    }

    @Test
    fun scannerSkipsWhitespace() {
        " \t\n\r+".firstToken().type.shouldBe(Token.Type.OPERATOR_ADD)
    }

    @Test
    fun scannerSkipsComments() {
        """
            // This should be recognized as a comment.
            +
        """.firstToken().type.shouldBe(Token.Type.OPERATOR_ADD)
    }

    @Test
    fun scannerRecognizesIdentifiers() {
        val token = "aB_0".firstToken()
        token.type.shouldBe(Token.Type.IDENTIFIER)
        token.value.shouldBe("aB_0")
    }

    @Test
    fun scannerRecognizesIntegers() {
        val token = "0123456789".firstToken()
        token.type.shouldBe(Token.Type.INTEGER)
        token.value.shouldBe("0123456789")
    }

    @Test
    fun scannerRecognizesSequence() {
        """
            identifier_ending_with_numbers123456789
            AAAA bbbb A_a routine
            0 1 2 3 4 5 6 7 8 9
            // All operators:
            + - ^ * / % == != < <= > >=
            // Arrows and remaining stuff
            <- -> ( ) { } : , . :=
        """.shouldContainTokensAndEOF(
            Token.Type.IDENTIFIER,
            Token.Type.IDENTIFIER,
            Token.Type.IDENTIFIER,
            Token.Type.IDENTIFIER,
            Token.Type.ROUTINE,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.INTEGER,
            Token.Type.OPERATOR_ADD,
            Token.Type.OPERATOR_SUB,
            Token.Type.OPERATOR_XOR,
            Token.Type.OPERATOR_MUL,
            Token.Type.OPERATOR_DIV,
            Token.Type.OPERATOR_MOD,
            Token.Type.OPERATOR_EQU,
            Token.Type.OPERATOR_NEQ,
            Token.Type.OPERATOR_LST,
            Token.Type.OPERATOR_LSE,
            Token.Type.OPERATOR_GRT,
            Token.Type.OPERATOR_GRE,
            Token.Type.ARROW_L,
            Token.Type.ARROW_R,
            Token.Type.PAREN_L,
            Token.Type.PAREN_R,
            Token.Type.CURL_L,
            Token.Type.CURL_R,
            Token.Type.COLON,
            Token.Type.COMMA,
            Token.Type.DOT,
            Token.Type.ASSIGNMENT
        )
    }

    @Test
    fun scannerRecognizesIncompleteTokens() {
        "= !".shouldContainTokensAndEOF(Token.Type.UNKNOWN, Token.Type.UNKNOWN)
    }

    @Test
    fun scannerRecognizesIncompleteRoutineKeywordAsIdentifier() {
        for (length in 1 until ("routine".length - 1)) {
            val token = "routine".substring(0, length).firstToken().type.shouldBe(Token.Type.IDENTIFIER)
        }

    }

    private fun String.shouldContainTokensAndEOF(vararg types: Token.Type) {
        val input = InputSource.fromString(this)
        val typesIterator = types.iterator()

        Scanner(input).use { scanner ->
            while (typesIterator.hasNext()) {
                val scannedToken = scanner.nextToken()
                val expectedTokenType = typesIterator.next()

                scannedToken.type.shouldBe(expectedTokenType)
            }

            val nextToken = scanner.nextToken()
            nextToken.type.shouldBe(Token.Type.EOF)
        }
    }

    private fun String.firstToken(): Token {
        val input = InputSource.fromString(this)
        return Scanner(input).use { scanner -> scanner.nextToken() }
    }
}