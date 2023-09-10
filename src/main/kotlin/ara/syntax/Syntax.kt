package ara.syntax

import ara.Direction
import ara.analysis.dataflow.DataflowSolution
import ara.analysis.live.LivenessDescriptor
import ara.control.Block
import ara.control.ControlGraph
import ara.position.Range
import ara.reporting.Message
import ara.types.Environment
import ara.types.Signature

sealed class Syntax {
    lateinit var range: Range

    data class Identifier(val name: String) : Syntax(), Comparable<Identifier> {
        override fun toString(): String = Message.quote(name)

        override fun compareTo(other: Identifier): Int =
            this.name.compareTo(other.name)
    }

    data class Program(val definitions: List<Definition>) {
        lateinit var environment: Environment
    }

    sealed class Definition : Syntax() {
        abstract val name: Identifier
    }

    data class TypeDefinition(
        override val name: Identifier,
        val type: Type
    ) : Definition()

    data class RoutineDefinition(
        override val name: Identifier,
        val inputParameters: List<Parameter>,
        val outputParameters: List<Parameter>,
        val body: List<Instruction>
    ) : Definition() {
        lateinit var localEnvironment: Environment
        lateinit var graph: ControlGraph
        lateinit var signature: Signature
        lateinit var liveness: DataflowSolution<Block, LivenessDescriptor>
    }

    data class Parameter(
        val name: Identifier,
        val type: Type?
    ) : Syntax()

    /**
     * Abstract superclass of all instruction variants.
     */
    sealed class Instruction : Syntax()

    sealed interface Control {
        val direction: Direction
        fun labels(): Collection<Identifier>
    }

    /**
     * An assignment of the form
     * ```
     *  dst := src MOD (lhs OP rhs)
     *  dst := src
     * ```
     */
    data class ArithmeticAssignment(
        val dst: ResourceExpression,
        val src: ResourceExpression,
        val arithmetic: ArithmeticModifier
    ) : Instruction()

    /**
     * An assignment exchanging multiple values, written as
     * ```
     * (dst1, dst2, ..., dstN) := (src1, src2, ..., srcN)
     * ```
     */
    data class MultiAssignment(
        val dstList: List<ResourceExpression>,
        val srcList: List<ResourceExpression>
    ) : Instruction()

    /**
     * An unconditional entry- or exit-point
     * ```
     *  -> Label
     *  Label <-
     * ```
     */
    data class Unconditional(
        override val direction: Direction,
        val label: Identifier
    ) : Instruction(), Control {
        override fun labels(): Collection<Identifier> = listOf(label)
    }

    /**
     * A conditional entry- or exit-point
     * ```
     *  (comparison) -> Label , Label
     *  Label , Label <- (comparison)
     * ```
     */
    data class Conditional(
        override val direction: Direction,
        val lhsLabel: Identifier,
        val rhsLabel: Identifier,
        val condition: ConditionalExpression
    ) : Instruction(), Control {
        override fun labels(): Collection<Identifier> = listOf(lhsLabel, rhsLabel)
    }

    /**
     * A call or uncall instruction
     * ```
     *  (Expr1, ..., Exprn) := call Routine (Expr1, ..., Exprm)
     *  (Expr1, ..., Exprn) := uncall Routine (Expr1, ..., Exprm)
     * ```
     */
    data class Call(
        val dstList: List<ResourceExpression>,
        val srcList: List<ResourceExpression>,
        val direction: Direction,
        val routine: Identifier
    ) : Instruction()


    /**
     * An expression that can be used to initialize or finalize some resource.
     */
    sealed class ResourceExpression : Typeable()

    /**
     * A literal integer. E.g.
     * ```
     *  3
     * ```
     */
    data class IntegerLiteral(
        val value: Int
    ) : ResourceExpression()

    /**
     * A literal structure. E.g.
     * ```
     * { x = 0, y = a }
     * ```
     */
    data class StructureLiteral(
        val members: List<Member>
    ) : ResourceExpression() {
        data class Member(
            val name: Identifier,
            val value: ResourceExpression
        ) : Syntax()
    }

    /**
     * An expression allocating some value in memory (during forward execution)
     * or releasing some value from memory (during backward direction).
     * ```
     * & value
     * ```
     */
    data class AllocationExpression(
        val value: ResourceExpression
    ) : ResourceExpression()

    /**
     * An expression used to access in-memory values.
     * ```
     *  storage.path&
     * ```
     */
    data class DereferencedStorage(
        val storage: Storage
    ) : Storage()

    /**
     * A storage is some modifiable value (e.g. local on the stack frame or in global memory).
     */
    sealed class Storage : ResourceExpression()

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
     * A storage describing access to a member of a complex value.
     * ```
     *  complex.member
     * ```
     */
    data class MemberAccess(
        val storage: Storage,
        val member: Identifier
    ) : Storage()

    /**
     * A modification made to some storage during an assignment.
     * ```
     *  MOD (expr)
     * ```
     */
    class ArithmeticModifier(
        val modificationOperator: ModificationOperator,
        val value: ArithmeticExpression
    ) : Syntax()

    enum class ModificationOperator {
        ADD, SUB, XOR
    }


    /**
     * An evaluable expression that neither initializes nor finalizes storage.
     */
    sealed class ArithmeticExpression : Typeable()

    /**
     * A binary expression.
     */
    data class ArithmeticBinary(
        val lhs: ResourceExpression,
        val operator: BinaryOperator,
        val rhs: ResourceExpression
    ) : ArithmeticExpression()

    /**
     *
     */
    data class ArithmeticValue(
        val value: ResourceExpression
    ) : ArithmeticExpression()

    enum class BinaryOperator {
        ADD, SUB, XOR, MUL, DIV, MOD
    }

    /**
     * An evaluable expression that neither initializes nor finalizes storage.
     */
    sealed class ConditionalExpression : Typeable()

    /**
     * A binary comparison.
     */
    data class ComparativeBinary(
        val lhs: ResourceExpression,
        val comparator: ComparisonOperator,
        val rhs: ResourceExpression
    ) : ConditionalExpression()

    enum class ComparisonOperator {
        EQU, NEQ, LST, LSE, GRT, GRE
    }

    /**
     * An expression representing a type.
     */
    sealed class Type : Syntax()

    /**
     * A type referenced by name.
     */
    data class NamedType(
        val name: Identifier
    ) : Type()

    /**
     * A type representing values in global memory.
     * ```
     * &Type
     * ```
     */
    data class ReferenceType(
        val baseType: Type
    ) : Type()

    /**
     * A type containing different members with own types.
     */
    data class StructureType(
        val members: List<Member>
    ) : Type() {
        data class Member(
            val name: Identifier,
            val type: Type
        ) : Syntax()
    }
}