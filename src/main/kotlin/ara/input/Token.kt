package ara.input

import ara.position.Range

data class Token(val type: Type, val value: String? = null) {
    lateinit var range: Range

    enum class Type {
        IDENTIFIER,
        INTEGER,

        COLON,
        COMMA,
        DOT,
        ASSIGNMENT,

        OPERATOR_ADD,
        OPERATOR_SUB,
        OPERATOR_XOR,
        OPERATOR_MUL,
        OPERATOR_DIV,
        OPERATOR_MOD,
        OPERATOR_EQU,
        OPERATOR_NEQ,
        OPERATOR_LST,
        OPERATOR_LSE,
        OPERATOR_GRT,
        OPERATOR_GRE,

        ARROW_L,
        ARROW_R,

        PAREN_L,
        PAREN_R,

        CURL_L,
        CURL_R,

        UNKNOWN,
        EOF
    }
}
