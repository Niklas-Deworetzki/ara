package ara.analysis.type

import ara.reporting.Message
import ara.syntax.Syntax
import ara.syntax.extensions.lookupVariableType
import ara.types.Type
import ara.types.TypeUnification
import ara.types.TypeUnification.unify
import ara.types.extensions.isInstantiated
import kotlin.math.max

interface TypeCheckingMixin {

    fun reportTypeError(message: Message): Message

    fun Set<Syntax.Identifier>.ensureInstantiated(routine: Syntax.RoutineDefinition, description: String) {
        val notInstantiatedNames = this.filterNot { routine.lookupVariableType(it)?.isInstantiated() ?: false }

        for (name in notInstantiatedNames) {
            val message = Message.error(
                "Type error",
                "Type of $description $name cannot be inferred. Perhaps some type annotations are missing?"
            )

            reportTypeError(message)
                .withPositionOf(name)
        }
    }

    fun Type.isDefinedAs(type: Type, by: Syntax, message: () -> String): Message? =
        typesMustBeTheSame(
            type, "Defined type",
            this, "Actual type",
            by, message
        )

    fun Type.mustBe(type: Type, by: Syntax, message: () -> String): Message? =
        typesMustBeTheSame(
            type, "Required type",
            this, "Actual type",
            by, message
        )

    fun typesMustBeTheSame(
        type1: Type, header1: String,
        type2: Type, header2: String,
        where: Syntax, message: () -> String
    ): Message? {
        val typeError = unify(type1, type2) ?: return null // Return if unification succeeds.
        val errorTrace = typeError.unfold()

        val reportedMessage = Message.error("Type error", message())
            .withPositionOf(where)
            .withAdditionalInfo(formatReportedType(header1, type1, header2))
            .withAdditionalInfo(formatReportedType(header2, type2, header1))

        for (error in errorTrace.filterNot { it is TypeUnification.Error.NotUnifiable }) {
            val cause = "Cause: " + formatTypeError(error)
            reportedMessage.withAdditionalInfo(cause)
        }
        return reportTypeError(reportedMessage)
    }

    private fun formatReportedType(
        header: String,
        type: Type,
        additionalHeaderForPadding: String
    ): String {
        val maximumHeaderLength = max(header.length, additionalHeaderForPadding.length)
        val padding = " ".repeat(maximumHeaderLength - header.length)
        return "$header: $padding$type"
    }

    private fun formatTypeError(error: TypeUnification.Error): String = when (error) {
        TypeUnification.Error.RecursiveType ->
            "Infinite type arising from constraints."

        is TypeUnification.Error.DifferentStructSize ->
            "Structure types have different sizes."

        is TypeUnification.Error.DifferentMemberNames ->
            "Structure types have members with different names at position ${error.index + 1} named ${error.aMember.name} and ${error.bMember.name}."

        is TypeUnification.Error.DifferentMemberTypes ->
            "Structure types have members types at position ${error.index + 1} named ${error.aMember.name}."

        is TypeUnification.Error.NotUnifiable ->
            throw NotImplementedError("Not unifable type error is not formatted as hint.")
    }
}
