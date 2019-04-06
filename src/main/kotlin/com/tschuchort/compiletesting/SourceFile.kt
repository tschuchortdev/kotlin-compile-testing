package com.tschuchort.compiletesting

import okio.buffer
import okio.sink
import java.io.File

/**
 * A source file for the [KotlinCompilation]
 */
abstract class SourceFile {
    internal abstract fun writeIfNeeded(dir: File): File

    companion object {
        /**
         * Create a new source file for the compilation when the compilation is run
         */
        fun new(name: String, contents: String) = object : SourceFile() {
            override fun writeIfNeeded(dir: File): File {
                val file = dir.resolve(name)
                file.createNewFile()

                file.sink().buffer().use {
                    it.writeUtf8(contents)
                }

                return file
            }
        }

        /**
         * Compile an existing source file
         */
        fun fromPath(path: File) = object : SourceFile() {
            init {
                require(path.isFile)
            }

            override fun writeIfNeeded(dir: File): File = path
        }
    }
}