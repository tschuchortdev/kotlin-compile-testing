package com.tschuchort.compiletesting

import okio.Buffer
import java.io.*
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import javax.lang.model.SourceVersion

internal fun <E> MutableCollection<E>.addAll(vararg elems: E) = addAll(elems)

internal fun getJavaHome(): File {
    val path = System.getProperty("java.home")
        ?: System.getenv("JAVA_HOME")
        ?: throw IllegalStateException("no java home found")

    return File(path).also { check(it.isDirectory) }
}

internal val processJdkHome by lazy {
    if(isJdk9OrLater())
        getJavaHome()
    else
        getJavaHome().parentFile
}

/** Checks if the JDK of the host process is version 9 or later */
internal fun isJdk9OrLater(): Boolean
        = SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0

internal fun File.listFilesRecursively(): List<File> {
    return (listFiles() ?: throw RuntimeException("listFiles() was null. File is not a directory or I/O error occured"))
        .flatMap { file ->
        if(file.isDirectory)
            file.listFilesRecursively()
        else
            listOf(file)
    }
}

internal fun Path.listFilesRecursively(): List<Path> {
    val files = mutableListOf<Path>()

    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            files.add(file)
            return FileVisitResult.CONTINUE
        }
    })

    return files
}

internal fun Path.hasKotlinFileExtension() = toFile().hasKotlinFileExtension()

internal fun Path.hasJavaFileExtension() = toFile().hasJavaFileExtension()

internal fun File.hasKotlinFileExtension() = hasFileExtension(listOf("kt", "kts"))

internal fun File.hasJavaFileExtension() = hasFileExtension(listOf("java"))

internal fun File.hasFileExtension(extensions: List<String>)
    = extensions.any{ it.equals(extension, ignoreCase = true) }

internal fun URLClassLoader.addUrl(url: URL) {
    val addUrlMethod = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
    addUrlMethod.isAccessible = true
    addUrlMethod.invoke(this, url)
}

internal inline fun <T> withSystemProperty(key: String, value: String, f: () -> T): T
        = withSystemProperties(mapOf(key to value), f)


internal inline fun <T> withSystemProperties(properties: Map<String, String>, f: () -> T): T {
    val previousProperties = mutableMapOf<String, String?>()

    for ((key, value) in properties) {
        previousProperties[key] = System.getProperty(key)
        System.setProperty(key, value)
    }

    try {
        return f()
    } finally {
        for ((key, value) in previousProperties) {
            if (value != null)
                System.setProperty(key, value)
        }
    }
}

internal inline fun <R> withSystemOut(stream: PrintStream, crossinline f: () -> R): R {
    System.setOut(stream)
    val ret = f()
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
    return ret
}

internal inline fun <R> captureSystemOut(crossinline f: () -> R): Pair<R, String> {
    val systemOutBuffer = Buffer()
    val ret = withSystemOut(PrintStream(systemOutBuffer.outputStream()), f)
    return Pair(ret, systemOutBuffer.readString(Charset.defaultCharset()))
}

internal fun File.existsOrNull(): File? = if (exists()) this else null