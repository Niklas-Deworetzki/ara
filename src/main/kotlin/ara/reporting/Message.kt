package ara.reporting

import ara.position.Range
import org.fusesource.jansi.Ansi

/**
 * Common interface for all message variants.
 */
class Message(
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
    val message: String,
    /**
     * A description of the source code associated with the message.
     */
    val range: Range? = null
) {

    companion object {
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