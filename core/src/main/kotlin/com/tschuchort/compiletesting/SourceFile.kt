package com.tschuchort.compiletesting

import okio.buffer
import okio.sink
import org.intellij.lang.annotations.Language
import java.io.File

/**
 * A source file for the [KotlinCompilation]
 */
abstract class SourceFile {
    internal abstract fun writeIfNeeded(dir: File): File

    /** Marks this source file as a source of the common module in a multiplatform project */
    abstract val isMultiplatformCommonSource: Boolean

    companion object {
        /**
         * Create a new Java source file for the compilation when the compilation is run
         *
         * @param isMultiplatformCommonSource marks this source file as a source of the common module in a multiplatform project
         */
        fun java(name: String, @Language("java") contents: String, trimIndent: Boolean = true, isMultiplatformCommonSource: Boolean = false): SourceFile {
            require(File(name).hasJavaFileExtension())
            val finalContents = if (trimIndent) contents.trimIndent() else contents
            return new(name, finalContents, isMultiplatformCommonSource = isMultiplatformCommonSource)
        }

        /**
         * Create a new Kotlin source file for the compilation when the compilation is run
         *
         * @param isMultiplatformCommonSource marks this source file as a source of the common module in a multiplatform project
         */
        fun kotlin(name: String, @Language("kotlin") contents: String, trimIndent: Boolean = true, isMultiplatformCommonSource: Boolean = false): SourceFile {
            require(File(name).hasKotlinFileExtension())
            val finalContents = if (trimIndent) contents.trimIndent() else contents
            return new(name, finalContents, isMultiplatformCommonSource = isMultiplatformCommonSource)
        }

        /**
         * Create a new source file for the compilation when the compilation is run
         *
         * @param isMultiplatformCommonSource marks this source file as a source of the common module in a multiplatform project
         */
        fun new(name: String, contents: String, isMultiplatformCommonSource: Boolean = false) = object : SourceFile() {
            override fun writeIfNeeded(dir: File): File {
                val file = dir.resolve(name)
                file.parentFile.mkdirs()
                file.createNewFile()

                file.sink().buffer().use {
                    it.writeUtf8(contents)
                }

                return file
            }

            override val isMultiplatformCommonSource: Boolean = isMultiplatformCommonSource
        }

        /**
         * Compile an existing source file
         *
         * @param isMultiplatformCommonSource marks this source file as a source of the common module in a multiplatform project
         */
        fun fromPath(path: File, isMultiplatformCommonSource: Boolean = false) = object : SourceFile() {
            init {
                require(path.isFile)
            }

            override fun writeIfNeeded(dir: File): File = path

            override val isMultiplatformCommonSource: Boolean = isMultiplatformCommonSource
        }
    }
}
