package ara.interpreter

import ara.Direction
import ara.position.Range
import ara.position.SourceMarker
import ara.syntax.Syntax

const val UNKNOWN_LINE_NUMBER = -1

object StackTraceFactory {

    fun <E : Throwable> fillInStackTrace(
        exception: E,
        callStack: List<StackFrame>,
        currentPosition: Range
    ) {
        exception.stackTrace = constructStackTrace(callStack, currentPosition).toTypedArray()
    }

    private fun constructStackTrace(callStack: List<StackFrame>, currentPosition: Range): List<StackTraceElement> {
        val traceBuffer = mutableListOf<StackTraceElement>()

        var where: Range? = currentPosition
        var routine: Syntax.Identifier
        for (frame in callStack) {
            routine = frame.routine.name
            traceBuffer.add(createStackTraceElement(frame.direction, where, routine))
            where = frame.caller?.range
        }

        return traceBuffer
    }

    private fun createStackTraceElement(
        direction: Direction,
        where: Range?,
        routine: Syntax.Identifier
    ): StackTraceElement {
        val classname = direction.choose("Forward", "Backward")
        val methodname = routine.name
        val filename = where?.input?.filename() ?: "Unknown"
        val line = getLineNumber(where)
        return StackTraceElement(classname, methodname, filename, line)
    }

    private fun getLineNumber(position: Range?): Int {
        if (position == null)
            return UNKNOWN_LINE_NUMBER
        return SourceMarker(position).lineNumber
    }
}
