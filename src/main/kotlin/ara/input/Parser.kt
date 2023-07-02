package ara.input

import ara.Direction
import ara.analysis.Analysis
import ara.input.Token.Type.*
import ara.reporting.Message
import ara.syntax.Syntax
import ara.utils.Either

class Parser(private val scanner: Scanner) : Analysis<Syntax.Program>() {
    private companion object {
        val BINARY_OPERATORS = mapOf(
            OPERATOR_ADD to Syntax.BinaryOperator.ADD,
            OPERATOR_SUB to Syntax.BinaryOperator.SUB,
            OPERATOR_XOR to Syntax.BinaryOperator.XOR,
            OPERATOR_MUL to Syntax.BinaryOperator.MUL,
            OPERATOR_DIV to Syntax.BinaryOperator.DIV,
            OPERATOR_MOD to Syntax.BinaryOperator.MOD
        )

        val COMPARISON_OPERATORS = mapOf(
            OPERATOR_EQU to Syntax.ComparisonOperator.EQU,
            OPERATOR_NEQ to Syntax.ComparisonOperator.NEQ,
            OPERATOR_LST to Syntax.ComparisonOperator.LST,
            OPERATOR_LSE to Syntax.ComparisonOperator.LSE,
            OPERATOR_GRT to Syntax.ComparisonOperator.GRT,
            OPERATOR_GRE to Syntax.ComparisonOperator.GRE
        )

        val MODIFICATION_OPERATORS = mapOf(
            OPERATOR_ADD to Syntax.ModificationOperator.ADD,
            OPERATOR_SUB to Syntax.ModificationOperator.SUB,
            OPERATOR_XOR to Syntax.ModificationOperator.XOR,
        )

        val CALL_DIRECTIONS = mapOf(
            CALL to Direction.FORWARD,
            UNCALL to Direction.BACKWARD
        )
    }

    private lateinit var currentToken: Token
    private var lookahead: Token = nextValidScannerToken()

    private fun next() {
        currentToken = lookahead
        lookahead = nextValidScannerToken()
    }

    private fun nextValidScannerToken(): Token {
        var token: Token
        do {
            token = scanner.nextToken()
        } while (token.type == COMMENT)
        return token
    }

    private fun <R : Syntax> parse(parser: () -> R): R {
        val startPosition = lookahead.range
        val result = parser()
        val endPosition = currentToken.range
        result.range = startPosition union endPosition
        return result
    }

    override fun runAnalysis(): Syntax.Program = parseProgram()

    private fun parseProgram(): Syntax.Program {
        val definitions = mutableListOf<Syntax.Definition>()
        while (lookahead.type !== EOF) {
            try {
                definitions.add(parseGlobalDefinition())
            } catch (syntaxError: RecoverableSyntaxError) {
                consumeUntil(ROUTINE, TYPE)
            }
        }
        return Syntax.Program(definitions)
    }

    private fun parseGlobalDefinition(): Syntax.Definition = when (lookahead.type) {
        ROUTINE -> parseRoutineDefinition()
        TYPE -> parseTypeDefinition()
        else -> syntaxError("type or routine definition")
    }

    private fun parseRoutineDefinition(): Syntax.RoutineDefinition = parse {
        nextTokenShouldBe(ROUTINE, "routine keyword")
        val name = parseIdentifier()
        val inputParameters = parseParameterList()
        nextTokenShouldBe(ARROW_R, "right arrow")
        val outputParameters = parseParameterList()
        val body = parseRoutineBody()
        Syntax.RoutineDefinition(name, inputParameters, outputParameters, body)
    }

    private fun parseParameterList(): List<Syntax.Parameter> {
        nextTokenShouldBe(PAREN_L, "start of parameter list")
        if (lookahead.type === PAREN_R) {
            next()
            return emptyList()
        }


        val parameters = mutableListOf<Syntax.Parameter>()
        while (true) {
            try {
                parameters.add(parseParameter())
            } catch (syntaxError: RecoverableSyntaxError) {
                consumeUntil(PAREN_R, COMMA)
            }

            when (lookahead.type) {
                PAREN_R -> break
                COMMA -> next()
                else -> syntaxError("another parameter or end of parameter list")
            }
        }
        next()
        return parameters
    }

    private fun parseParameter(): Syntax.Parameter = parse {
        val name = parseIdentifier()
        var type: Syntax.Type? = null
        if (lookahead.type === COLON) {
            next()
            type = parseType()
        }
        Syntax.Parameter(name, type)
    }

    private fun parseRoutineBody(): List<Syntax.Instruction> {
        nextTokenShouldBe(CURL_L, "start of routine body")
        val instructions = mutableListOf<Syntax.Instruction>()
        try {
            while (lookahead.type !== CURL_R) {
                instructions.add(parseInstruction())
            }
        } catch (syntaxError: RecoverableSyntaxError) {
            consumeUntil(CURL_R)
        }
        next()
        return instructions
    }

    private fun parseTypeDefinition(): Syntax.TypeDefinition = parse {
        nextTokenShouldBe(TYPE, "type keyword")
        val name = parseIdentifier()
        nextTokenShouldBe(EQ, "equality operator")
        val type = parseType()
        Syntax.TypeDefinition(name, type)
    }

    private fun parseType(): Syntax.Type = when (lookahead.type) {
        CURL_L -> parseStructureType()
        IDENTIFIER -> parseNamedType()
        else -> syntaxError("type")
    }

    private fun parseStructureType(): Syntax.StructureType = parse {
        nextTokenShouldBe(CURL_L, "start of structure type")
        if (lookahead.type === CURL_R) {
            next()
            Syntax.StructureType(emptyList())
        } else {
            val members = mutableListOf<Syntax.Member>()
            while (true) {
                try {
                    members.add(parseMember())
                } catch (syntaxError: RecoverableSyntaxError) {
                    consumeUntil(CURL_R, COMMA)
                }

                when (lookahead.type) {
                    CURL_R -> break
                    COMMA -> next()
                    else -> syntaxError("another member or end of structure type")
                }
            }
            next()
            Syntax.StructureType(members)
        }
    }

    private fun parseMember(): Syntax.Member = parse {
        val name = parseIdentifier()
        nextTokenShouldBe(COLON, "type annotation")
        val type = parseType()
        Syntax.Member(name, type)
    }

    private fun parseNamedType(): Syntax.NamedType = parse {
        val name = parseIdentifier()
        Syntax.NamedType(name)
    }

    private fun parseInstruction(): Syntax.Instruction = when (lookahead.type) {
        IDENTIFIER, OPERATOR_SUB, INTEGER -> parseAssignmentOrEntry()
        PAREN_L -> parseConditionalExitOrCall()
        ARROW_R -> parseUnconditionalExit()
        else -> syntaxError("start of instruction")
    }

    private fun parseAssignmentOrEntry(): Syntax.Instruction = parse {
        val dst = parseResourceExpression()
        when {
            dst is Syntax.NamedStorage && lookahead.type === ARROW_L -> { // Unconditional entry
                next()
                Syntax.Unconditional(Direction.BACKWARD, dst.name)
            }

            dst is Syntax.NamedStorage && lookahead.type === COMMA -> { // Conditional entry
                next()
                val additionalLabel = parseIdentifier()
                nextTokenShouldBe(ARROW_L, "arrow from conditional entry point")
                val condition = parseConditionalExpression()
                Syntax.Conditional(Direction.BACKWARD, dst.name, additionalLabel, condition)
            }

            lookahead.type === ASSIGNMENT -> { // Assignment
                next()
                val src = parseResourceExpression()
                val modifier = parseOptionalArithmeticModifier()
                Syntax.Assignment(dst, src, modifier)
            }

            else -> syntaxError("assignment or entry point")
        }
    }

    private fun parseConditionalExitOrCall(): Syntax.Instruction = parse {
        when (val parenthesized = conditionalOrArgumentList()) {
            is Either.Left -> { // Conditional
                nextTokenShouldBe(ARROW_R, "right arrow for exit point")
                val lhsLabel = parseIdentifier()
                nextTokenShouldBe(COMMA, "comma and second label")
                val rhsLabel = parseIdentifier()
                Syntax.Conditional(Direction.FORWARD, lhsLabel, rhsLabel, parenthesized.left)
            }

            is Either.Right -> { // Call
                nextTokenShouldBe(ASSIGNMENT, "assignment operator")
                val direction = parseCallDirection()
                val calledRoutine = parseIdentifier()
                val inputArguments = parseArgumentList()
                Syntax.Call(parenthesized.right, inputArguments, direction, calledRoutine)
            }
        }
    }

    private fun conditionalOrArgumentList(): Either<Syntax.ConditionalExpression, List<Syntax.ResourceExpression>> {
        next() // Skip over opening parenthesis
        val firstValue = parseResourceExpression()
        val comparator = COMPARISON_OPERATORS[lookahead.type]
        return when {
            comparator != null -> {
                next()
                val secondValue = parseResourceExpression()
                nextTokenShouldBe(PAREN_R, "closing parenthesis")
                Either.left(Syntax.ComparativeBinary(firstValue, comparator, secondValue))
            }

            else -> {
                val arguments = mutableListOf(firstValue)
                while (lookahead.type !== PAREN_R) {
                    nextTokenShouldBe(COMMA, "another argument or end of argument list")
                    arguments.add(parseResourceExpression())
                }
                next()
                Either.right(arguments)
            }
        }
    }

    private fun parseCallDirection(): Direction {
        val direction = CALL_DIRECTIONS[lookahead.type] ?: syntaxError("call or uncall")
        next()
        return direction
    }

    private fun parseArgumentList(): List<Syntax.ResourceExpression> {
        nextTokenShouldBe(PAREN_L, "start of argument list")
        if (lookahead.type === PAREN_R) {
            next()
            return emptyList()
        }


        val arguments = mutableListOf<Syntax.ResourceExpression>()
        while (true) {
            try {
                arguments.add(parseResourceExpression())
            } catch (syntaxError: RecoverableSyntaxError) {
                consumeUntil(PAREN_R, COMMA)
            }

            when (lookahead.type) {
                PAREN_R -> break
                COMMA -> next()
                else -> syntaxError("another parameter or end of parameter list")
            }
        }
        next()
        return arguments
    }

    private fun parseOptionalArithmeticModifier(): Syntax.ArithmeticModifier? {
        val operator = MODIFICATION_OPERATORS[lookahead.type]
        return if (operator != null) parse {
            next()
            val value = parseArithmeticExpression()
            Syntax.ArithmeticModifier(operator, value)
        } else null
    }

    private fun parseUnconditionalExit(): Syntax.Unconditional = parse {
        nextTokenShouldBe(ARROW_R, "right arrow for exit point")
        val label = parseIdentifier()
        Syntax.Unconditional(Direction.FORWARD, label)
    }

    private fun parseResourceExpression(): Syntax.ResourceExpression = when (lookahead.type) {
        OPERATOR_SUB, INTEGER -> parseIntegerLiteral()
        IDENTIFIER -> parseStorage()
        else -> syntaxError("resource expression")
    }

    private fun parseIntegerLiteral(): Syntax.IntegerLiteral = parse {
        val isNegative = parseOptionalNegativeSign()
        val literal = nextTokenShouldBe(INTEGER, "integer literal")?.toIntOrNull()
            ?: syntaxError("valid integer literal")
        Syntax.IntegerLiteral(if (isNegative) -literal else literal)
    }

    private fun parseOptionalNegativeSign(): Boolean {
        if (lookahead.type === OPERATOR_SUB) {
            next()
            return true
        }
        return false
    }

    private fun parseStorage(): Syntax.Storage {
        val base = parse {
            Syntax.NamedStorage(parseIdentifier())
        }

        var storage: Syntax.Storage = base
        while (true) {
            storage = when (lookahead.type) {
                DOT -> {
                    next()
                    val member = parseIdentifier()
                    Syntax.MemberAccess(storage, member)
                }

                COLON -> {
                    next()
                    val type = parseType()
                    Syntax.TypedStorage(storage, type)
                }

                else -> break
            }

            storage.range = base.range union currentToken.range
        }
        return storage
    }

    private fun parseConditionalExpression(): Syntax.ConditionalExpression = parse {
        nextTokenShouldBe(PAREN_L, "opening parenthesis for condition")
        val lhs = parseResourceExpression()
        val comparator = COMPARISON_OPERATORS[lookahead.type] ?: syntaxError("comparison operator")
        next()
        val rhs = parseResourceExpression()
        nextTokenShouldBe(PAREN_R, "closing parenthesis for condition")
        Syntax.ComparativeBinary(lhs, comparator, rhs)
    }

    private fun parseArithmeticExpression(): Syntax.ArithmeticExpression = parse {
        // TODO: Allow atomic expressions here.
        nextTokenShouldBe(PAREN_L, "opening parenthesis of arithmetic expression")
        val lhs = parseResourceExpression()
        val operator = parseOptionalBinaryOperator()
        val result =
            if (operator == null) Syntax.ArithmeticValue(lhs)
            else {
                val rhs = parseResourceExpression()
                Syntax.ArithmeticBinary(lhs, operator, rhs)
            }
        nextTokenShouldBe(PAREN_R, "closing parenthesis of arithmetic expression")
        result
    }

    private fun parseOptionalBinaryOperator(): Syntax.BinaryOperator? {
        val operator = BINARY_OPERATORS[lookahead.type]
        if (operator != null) {
            next()
        }
        return operator
    }

    private fun parseIdentifier(): Syntax.Identifier = parse {
        val name = nextTokenShouldBe(IDENTIFIER, "identifier")
        Syntax.Identifier(name!!)
    }


    private fun nextTokenShouldBe(expectedType: Token.Type, message: String): String? {
        if (lookahead.type !== expectedType) {
            syntaxError(message)
        }
        next()
        return currentToken.value
    }

    private fun consumeUntil(vararg types: Token.Type) {
        val expectedTypes = types.toSet() + EOF
        while (lookahead.type !in expectedTypes) {
            next()
        }
    }

    // TODO: Error reporting could be improved by providing boundaries in the parser for the currently parsed construct.
    private fun syntaxError(expected: String): Nothing {
        val message = Message.error("Syntax error", "Expected $expected.")
            .withPosition(lookahead.range)
        reportError(message)
        throw RecoverableSyntaxError()
    }

    private class RecoverableSyntaxError : RuntimeException()
}