package ara.interpreter

import ara.Direction
import ara.position.Range
import ara.position.SourceMarker
import ara.syntax.Syntax
import java.io.File

class NativeException(cause: Exception) : Exception(cause) {

    override fun toString(): String =
        cause!!.toString()

    companion object {
        private const val UNKNOWN_LINE_NUMBER = -1

        fun withStackTraceFrom(exception: Exception, callStack: Iterable<StackFrame>, origin: Range): Exception {
            val result = NativeException(exception)
            result.stackTrace = constructStackTrace(callStack, origin)
            return result
        }

        private fun constructStackTrace(callStack: Iterable<StackFrame>, origin: Range): Array<StackTraceElement> {
            val traceBuffer = mutableListOf<StackTraceElement>()

            var where: Range? = origin
            var routine: Syntax.Identifier
            for (frame in callStack) {
                routine = frame.routine.name
                traceBuffer.add(createStackTraceElement(frame.direction, where, routine))
                where = frame.caller?.range
            }

            return traceBuffer.toTypedArray()
        }

        private fun createStackTraceElement(
            direction: Direction,
            where: Range?,
            routine: Syntax.Identifier
        ): StackTraceElement {
            val classname = direction.choose("Forward", "Backward")
            val methodname = routine.name
            val filename = getFileName(where)
            val line = getLineNumber(where)
            return StackTraceElement(classname, methodname, filename, line)
        }

        private fun getFileName(position: Range?): String {
            val filename = position?.input?.filename() ?: return "Unknown"
            return File(filename).name
        }

        private fun getLineNumber(position: Range?): Int {
            if (position == null)
                return UNKNOWN_LINE_NUMBER
            return SourceMarker(position).lineNumber
        }
    }
}