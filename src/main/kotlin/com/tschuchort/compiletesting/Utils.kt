package com.tschuchort.compiletesting

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import javax.lang.model.SourceVersion

internal fun <E> MutableCollection<E>.addAll(vararg elems: E) = addAll(elems)

internal fun getJavaHome(): File {
    val path = System.getProperty("java.home")
        ?: System.getenv("JAVA_HOME")
        ?: throw IllegalStateException("no java home found")

    return File(path).also { check(it.isDirectory) }
}

internal fun getJdkHome()
    = if(isJdk9OrLater())
        getJavaHome()
    else
        getJavaHome().parentFile

/** Checks if the JDK of the host process is version 9 or later */
internal fun isJdk9OrLater(): Boolean
        = SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0

internal fun File.listFilesRecursively(): List<File> {
    return listFiles().flatMap { file ->
        if(file.isDirectory)
            file.listFilesRecursively()
        else
            listOf(file)
    }
}

internal fun File.isKotlinFile()
        = listOf("kt", "kts").any{ it.equals(extension, ignoreCase = true) }

internal fun File.isJavaFile()
        = listOf("java").any{ it.equals(extension, ignoreCase = true) }

internal fun URLClassLoader.addUrl(url: URL) {
    val addUrlMethod = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
    addUrlMethod.isAccessible = true
    addUrlMethod.invoke(this, url)
}