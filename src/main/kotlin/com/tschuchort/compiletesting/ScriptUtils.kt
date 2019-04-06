package com.tschuchort.compiletesting


import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name
import java.net.URLClassLoader

fun KotlinCompilation.Result.runCompiledScript(
    fileName: String,
    args: List<String> = emptyList(),
    classLoader: ClassLoader? = Thread.currentThread().contextClassLoader
) {
    fun String.removeKotlinFileSuffix() = removeSuffix(".kt").removeSuffix(".kts")

    val scriptClassName = Name.identifier(
        PackagePartClassUtils.getFilePartShortName(fileName.removeKotlinFileSuffix())
    ).identifier.removeSuffix("Kt")

    val scriptClassLoader = URLClassLoader(arrayOf(outputDirectory.toURI().toURL()), classLoader)

    val scriptClass = try {
        scriptClassLoader.loadClass(scriptClassName)
    } catch (e: ClassNotFoundException) {
        throw IllegalArgumentException("Could not load script class for given filename $fileName", e)
    }

    scriptClass.getConstructor(Array<String>::class.java).newInstance(args.toTypedArray())
}

