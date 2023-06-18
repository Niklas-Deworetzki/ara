package ara.reporting

import ara.position.Range
import ara.syntax.Syntax
import org.fusesource.jansi.Ansi

/**
 * Common interface for all message variants.
 */
class Message
private constructor(
    /**
     * A highlighting color used when formatting the message.
     */
    val color: Ansi.Color,
    /**
     * Provides the message type as a [String] used as a
     * header for a formatted message. This should be capitalized.
     */
    val type: String,
    /**
     * Contents of the message.
     */
    val message: String
) {
    /**
     * A description of the source code associated with the message.
     */
    var range: Range? = null

    /**
     * Additional information that may be provided for the message.
     */
    val additionalInfo: MutableList<AdditionalInfo> = mutableListOf()


    fun withPosition(range: Range): Message {
        this.range = range
        return this
    }

    fun withPositionOf(syntax: Syntax): Message {
        this.range = syntax.range
        return this
    }

    fun withAdditionalInfo(description: String, range: Range? = null): Message {
        this.additionalInfo.add(AdditionalInfo(description, range))
        return this
    }


    class AdditionalInfo(val description: String, val range: Range?)

    companion object {

        fun error(type: String = "Error", message: String): Message =
            Message(Ansi.Color.RED, type, message)

        /**
         * Returns a [String] representation of the given [Object]
         * with added quotes. Used to highlight identifiers and mark them as
         * different from the remaining text.
         */
        fun quote(obj: Any): String {
            return "`$obj'"
        }

        fun Any.quoted(): String =
            quote(this)
    }
}