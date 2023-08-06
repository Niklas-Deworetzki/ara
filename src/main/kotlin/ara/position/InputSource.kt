package ara.position

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader

interface InputSource {

    fun open(): Reader

    fun filename(): String?

    companion object {
        fun fromString(string: String): InputSource =
            StringInput(string)

        fun fromFile(file: File): InputSource =
            FileInput(file)
    }

    private class FileInput(private val file: File) : InputSource {
        override fun open(): Reader =
            InputStreamReader(FileInputStream(file))

        override fun filename(): String? =
            file.path
    }

    private class StringInput(private val string: String) : InputSource {
        override fun open(): Reader =
            StringReader(string)

        override fun filename(): String? =
            null
    }
}