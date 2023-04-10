package ara.syntax

import ara.Direction
import ara.position.Range
import ara.reporting.Message

sealed class Syntax {
    var range: Range? = null

    data class Identifier(val name: String) : Syntax() {
        override fun toString(): String = Message.quote(name)
    }

    data class Program(
        val declarations: List<Declaration>
    )

    sealed class Declaration : Syntax() {
        abstract val name: Identifier
    }

    data class TypeDeclaration(
        override val name: Identifier,
        val type: Type
    ) : Declaration()

    data class RoutineDeclaration(
        override val name: Identifier,
        val inputParameters: List<Parameter>,
        val outputParameters: List<Parameter>,
        val body: List<Instruction>
    ) : Declaration() {
        lateinit var localScope: Map<Identifier, ara.types.Type>
    }

    data class Parameter(
        val name: Identifier,
        val type: Type
    ) : Syntax()

    /**
     * Abstract superclass of all instruction variants.
     */
    sealed class Instruction : Syntax()

    /**
     * An assignment of the form
     * ```
     *  dst := src MOD (lhs OP rhs)
     *  dst := src
     * ```
     */
    data class Assignment(
        val dst: Expression,
        val src: Expression,
        val arithmetic: ArithmeticModifier?
    ) : Instruction()

    /**
     * An unconditional entry- or exit-point
     * ```
     *  -> Label
     *  Label <-
     * ```
     */
    data class Unconditional(
        val direction: Direction,
        val label: Identifier
    ) : Instruction()

    /**
     * A conditional entry- or exit-point
     * ```
     *  (comparison) -> Label , Label
     *  Label , Label <- (comparison)
     * ```
     */
    data class Conditional(
        val direction: Direction,
        val lhsLabel: Identifier,
        val rhsLabel: Identifier,
        val comparison: ArithmeticExpression
    ) : Instruction()

    /**
     * A call or uncall instruction
     * ```
     *  (Expr1, ..., Exprn) := call Routine (Expr1, ..., Exprm)
     *  (Expr1, ..., Exprn) := uncall Routine (Expr1, ..., Exprm)
     * ```
     */
    data class Call(
        val dstList: List<Expression>,
        val srcList: List<Expression>,
        val direction: Direction,
        val routine: Identifier
    ) : Instruction()


    /**
     * Abstract superclass of all expression variants.
     */
    sealed class Expression : Syntax()

    /**
     * A literal integer. E.g.
     * ```
     *  3
     * ```
     */
    data class IntegerLiteral(
        val value: Int
    ) : Expression()

    /**
     * An expression allocating a new object in memory (during forward execution)
     * or deleting an object from memory (during backward direction). Objects are
     * always in a zeroed state.
     * ```
     *  new Type
     *  del Type
     * ```
     */
    data class AllocationExpression(
        val direction: Direction,
        val type: Type
    ) : Expression()

    /**
     * An expression used to access in-memory values.
     * ```
     *  ~ Expr
     * ```
     */
    data class ReferenceExpression(
        val storage: Storage
    ) : Expression()


    /**
     * Abstract superclass of all storage variants, which are used to
     * describe either local or in-memory values.
     */
    sealed class Storage : Expression()

    /**
     * Associates a variable with a type.
     * ```
     *  x : Type
     * ```
     */
    data class TypedStorage(
        val storage: Storage,
        val type: Type
    ) : Storage()

    /**
     * A simple variable without type annotation.
     * The type must be inferred. E.g.
     * ```
     *  x
     * ```
     */
    data class NamedStorage(
        val name: Identifier
    ) : Storage()

    /**
     * A storage describing access to a member of a complex value.
     * ```
     *  complex.member
     * ```
     */
    data class MemberAccess(
        val storage: Storage,
        val member: Identifier
    ) : Storage()


    class ArithmeticModifier(
        val modificationOperator: ModificationOperator,
        val value: ArithmeticExpression
    ) : Syntax()

    enum class ModificationOperator {
        ADD, SUB, XOR
    }


    sealed class ArithmeticExpression : Syntax()

    data class ArithmeticBinary(
        val lhs: Expression,
        val operator: BinaryOperator,
        val rhs: Expression
    ) : ArithmeticExpression()

    data class ArithmeticValue(
        val value: Expression
    ) : ArithmeticExpression()

    enum class BinaryOperator {
        ADD, SUB, XOR, MUL, DIV, MOD, EQU, NEQ, LST, LSE, GRT, GRE
    }


    sealed class Type : Syntax()

    data class NamedType(
        val name: Identifier
    ) : Type()

    data class ReferenceType(
        val baseType: Type
    ) : Type()

    data class StructureType(
        val members: List<Member>
    ) : Type()

    data class Member(
        val name: Identifier,
        val type: Type
    ) : Syntax()
}