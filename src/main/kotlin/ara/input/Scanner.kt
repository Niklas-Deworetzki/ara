package ara.input

import ara.input.Token.Type.*
import ara.position.InputSource
import ara.position.Range
import java.io.Closeable
import java.lang.StringBuilder
import java.util.*

class Scanner(private val input: InputSource) : Closeable {
    private val reader = input.open()

    private var buffer: Deque<Int> = ArrayDeque()

    private var currentCharCode: Int = END_OF_FILE
    private val currentChar: Char
        get() = currentCharCode.toChar()

    private var startOfCurrentToken: Long = -1L
    private var currentOffset: Long = -1L

    private fun advance() {
        currentOffset++
        if (buffer.isEmpty()) {
            currentCharCode = reader.read()
        } else {
            currentCharCode = buffer.pop()
        }
    }

    private fun revert(vararg additionalChars: Char) {
        currentOffset -= additionalChars.size + 1
        buffer.push(currentCharCode)
        for (char in additionalChars.reversed()) {
            buffer.push(char.code)
        }
    }

    private fun createToken(type: Token.Type, value: String? = null): Token {
        val token = Token(type, value)
        token.range = Range(input, startOfCurrentToken, currentOffset + 1)
        return token
    }

    companion object {
        private const val END_OF_FILE: Int = -1

        private val SIMPLE_TOKEN_TYPES = mapOf(
            ',' to COMMA,
            '.' to DOT,
            '+' to OPERATOR_ADD,
            '^' to OPERATOR_XOR,
            '*' to OPERATOR_MUL,
            '/' to OPERATOR_DIV,
            '%' to OPERATOR_MOD,
            '(' to PAREN_L,
            ')' to PAREN_R,
            '{' to CURL_L,
            '}' to CURL_R,
        )

        private fun representsIntegerDigit(code: Int): Boolean =
            code >= '0'.code && code <= '9'.code
    }

    private fun consumeWhitespace(): Boolean {
        val startOffset = currentOffset
        while (Character.isWhitespace(currentCharCode)) {
            advance()
        }
        return currentOffset > startOffset
    }

    private fun consumeComment(): Boolean {
        if (currentCharCode != '/'.code) {
            return false
        }

        advance()
        if (currentCharCode != '/'.code) {
            revert('/')
            return false
        }

        do {
            advance()
        } while (currentCharCode != '\n'.code)
        return true
    }

    fun nextToken(): Token {
        advance()
        while (consumeComment() or consumeWhitespace()) {
            // Repeat until nothing is left to consume.
        }

        startOfCurrentToken = currentOffset
        if (currentCharCode == END_OF_FILE) return createToken(EOF)

        val simpleType = SIMPLE_TOKEN_TYPES[currentChar]
        if (simpleType != null) return createToken(simpleType)

        return when (currentChar) {
            ':' ->
                parseContinuousToken(COLON, '=' to ASSIGNMENT)

            '-' ->
                parseContinuousToken(OPERATOR_SUB, '>' to ARROW_R)

            '<' ->
                parseContinuousToken(OPERATOR_LST, '=' to OPERATOR_LSE, '-' to ARROW_L)

            '>' ->
                parseContinuousToken(OPERATOR_GRT, '=' to OPERATOR_GRE)

            '=' ->
                parseContinuousToken(EQ, '=' to OPERATOR_EQU)

            '!' ->
                parseContinuousToken(UNKNOWN, '=' to OPERATOR_NEQ)

            else -> when {
                Character.isJavaIdentifierStart(currentChar) ->
                    consumeCharacters(IDENTIFIER, Character::isJavaIdentifierPart)

                representsIntegerDigit(currentCharCode) ->
                    consumeCharacters(INTEGER, ::representsIntegerDigit)

                else ->
                    createToken(UNKNOWN)
            }
        }
    }

    private fun parseContinuousToken(
        default: Token.Type,
        vararg possibleTokens: Pair<Char, Token.Type>
    ): Token {
        advance()
        if (currentCharCode == END_OF_FILE) {
            revert()
            return createToken(default)
        }

        for ((tokenChar, tokenType) in possibleTokens) {
            if (currentChar == tokenChar)
                return createToken(tokenType)
        }
        revert()
        return createToken(default)
    }

    private inline fun consumeCharacters(type: Token.Type, predicate: (Int) -> Boolean): Token {
        val buffer = StringBuilder()

        do {
            buffer.append(currentChar)
            advance()
        } while (predicate(currentCharCode))
        revert()

        return when (val bufferContents = buffer.toString()) {
            "routine" -> createToken(ROUTINE)
            "type" -> createToken(TYPE)
            "call" -> createToken(CALL)
            "uncall" -> createToken(UNCALL)
            else -> createToken(type, bufferContents)
        }
    }

    override fun close() {
        reader.close()
    }
}