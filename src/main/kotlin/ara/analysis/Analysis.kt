package ara.analysis

import ara.position.Range
import ara.reporting.Message
import ara.syntax.Syntax
import org.fusesource.jansi.Ansi

abstract class Analysis<T> {
    abstract val program: Syntax.Program

    abstract fun runAnalysis(): T


    private val _reportedMessages: MutableList<Message> = mutableListOf()

    val reportedMessages: List<Message>
        get() = _reportedMessages


    private fun mkErrorMessage(message: String, range: Range?): Message = Message(
        Ansi.Color.RED,
        "Error",
        message,
        range
    )

    fun reportError(message: String) =
        _reportedMessages.add(mkErrorMessage(message, null))

    fun reportError(range: Range, message: String) =
        _reportedMessages.add(mkErrorMessage(message, range))

    fun reportError(syntax: Syntax, message: String) =
        _reportedMessages.add(mkErrorMessage(message, syntax.range))
}