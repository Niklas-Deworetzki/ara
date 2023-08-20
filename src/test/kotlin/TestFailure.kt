class TestFailure(
    specification: TestSpecification,
    message: String,
    val unexpectedErrors: List<String> = emptyList(),
    val missingErrors: List<String> = emptyList()
) : AssertionError(message) {
    init {
        stackTrace = arrayOf(specification.toStackTraceElement())
    }

    override fun toString(): String = when {
        unexpectedErrors.isNotEmpty() ->
            formatExpectations("Unexpected errors reported:", unexpectedErrors)

        missingErrors.isNotEmpty() ->
            formatExpectations("Unreported errors expected:", missingErrors)

        else ->
            "$message"
    }

    private fun formatExpectations(caption: String, errors: List<String>): String {
        val lines = mutableListOf("$message$caption")
        for (error in errors) {
            lines.add("# $error")
        }
        return lines.joinToString(separator = System.lineSeparator())
    }

    private companion object {
        fun TestSpecification.toStackTraceElement(): StackTraceElement =
            StackTraceElement(
                "Test",
                "failure",
                this.file.name,
                1
            )
    }
}
