package ara.analysis

import ara.position.Range
import ara.reporting.Message
import ara.syntax.Syntax
import org.fusesource.jansi.Ansi

abstract class Analysis<T> {
    abstract fun runAnalysis(): T

    protected inline fun proceedAnalysis(action: () -> Unit) {
        if (!hasReportedErrors) action()
    }


    private val _reportedErrors: MutableList<Message> = mutableListOf()

    val reportedErrors: List<Message>
        get() = _reportedErrors

    val hasReportedErrors: Boolean
        get() = _reportedErrors.isNotEmpty()

    private fun mkErrorMessage(message: String, range: Range?): Message = Message(
        Ansi.Color.RED,
        "Error",
        message,
        range
    )

    fun reportError(message: Message) {
        _reportedErrors.add(message)
    }

    fun reportError(message: String) =
        reportError(mkErrorMessage(message, null))

    fun reportError(range: Range, message: String) =
        reportError(mkErrorMessage(message, range))

    fun reportError(syntax: Syntax, message: String) =
        reportError(mkErrorMessage(message, syntax.range))
}

