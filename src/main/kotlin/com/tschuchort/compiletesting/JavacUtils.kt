package com.tschuchort.compiletesting

import okio.Buffer
import java.io.*
import java.lang.IllegalArgumentException
import java.net.URI
import java.nio.charset.Charset
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject

/**
 * A [JavaFileObject] created from a source [File].
 *
 * Used for interfacing with javac ([JavaCompiler]).
 */
internal class FileJavaFileObject(val sourceFile: File, val charset: Charset = Charset.defaultCharset())
    : SimpleJavaFileObject(sourceFile.toURI(),
    deduceKind(sourceFile.toURI())
) {

    init {
        require(sourceFile.isFile)
        require(sourceFile.canRead())
    }

    companion object {
        private fun deduceKind(uri: URI): JavaFileObject.Kind
            = JavaFileObject.Kind.values().firstOrNull {
                uri.path.endsWith(it.extension, ignoreCase = true)
            } ?: JavaFileObject.Kind.OTHER
    }

    override fun openInputStream(): InputStream = sourceFile.inputStream()

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence
            = sourceFile.readText(charset)
}

/**
 * A [JavaFileObject] created from a [String].
 *
 * Used for interfacing with javac ([JavaCompiler]).
 */
internal class StringJavaFileObject(className: String, private val contents: String)
    : SimpleJavaFileObject(
    URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
    JavaFileObject.Kind.SOURCE
){
    private var _lastModified = System.currentTimeMillis()

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = contents

    override fun openInputStream(): InputStream
            = ByteArrayInputStream(contents.toByteArray(Charset.defaultCharset()))

    override fun openReader(ignoreEncodingErrors: Boolean): Reader = StringReader(contents)

    override fun getLastModified(): Long = _lastModified
}

/**
 * Gets the version string of a javac executable that can be started using the
 * given [javacCommand] via [Runtime.exec].
 */
internal fun getJavacVersionString(javacCommand: String): String {
    val javacProc = ProcessBuilder(listOf(javacCommand, "-version"))
        .redirectErrorStream(true)
        .start()

    val buffer = Buffer()

    javacProc.inputStream.copyTo(buffer.outputStream())
    javacProc.waitFor()

    val output = buffer.readUtf8()

    return Regex("javac (.*)?[\\s\\S]*").matchEntire(output)?.destructured?.component1()
        ?: throw IllegalStateException("Command '$javacCommand -version' did not print expected output. " +
                "Output was: '$output'")
}

internal fun isJavac9OrLater(javacVersionString: String): Boolean {
    val (majorv, minorv, patchv, otherv) = Regex("([0-9]*)\\.([0-9]*)\\.([0-9]*)(.*)")
        .matchEntire(javacVersionString)?.destructured
        ?: throw IllegalArgumentException("Could not parse javac version string: '$javacVersionString'")

    return (majorv.toInt() == 1 && minorv.toInt() >= 9) // old versioning scheme: 1.8.x
            || (majorv.toInt() >= 9) // new versioning scheme: 10.x.x
}