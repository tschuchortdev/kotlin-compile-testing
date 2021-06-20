package com.tschuchort.compiletesting

import java.io.*
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

internal fun isJavac9OrLater(): Boolean {
    return isJavac9OrLater(System.getProperty("java.specification.version"))
}

internal fun isJavac9OrLater(specVersion: String): Boolean {
    val version = specVersion.toIntOrNull() ?: return false
    return version >= 9
}

/** Finds the tools.jar given a path to a JDK 8 or earlier */
internal fun findToolsJarFromJdk(jdkHome: File): File {
    return jdkHome.resolve("../lib/tools.jar").existsOrNull()
        ?: jdkHome.resolve("lib/tools.jar").existsOrNull()
        ?: jdkHome.resolve("tools.jar").existsOrNull()
        ?: throw IllegalStateException("Can not find tools.jar from JDK with path ${jdkHome.absolutePath}")
}