package com.tschuchort.compiletesting

import io.github.classgraph.ClassGraph
import org.assertj.core.api.Assertions
import java.io.File

fun defaultCompilerConfig(): KotlinCompilation {
    return KotlinCompilation( ).apply {
        inheritClassPath = false
        skipRuntimeVersionCheck = true
        correctErrorTypes = true
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
    }
}

fun defaultJsCompilerConfig(): KotlinJsCompilation {
    return KotlinJsCompilation( ).apply {
        inheritClassPath = false
        verbose = true
        reportOutputFiles = false
        messageOutputStream = System.out
    }
}


fun assertClassLoadable(compileResult: KotlinCompilation.Result, className: String): Class<*> {
    try {
        val clazz = compileResult.classLoader.loadClass(className)
        Assertions.assertThat(clazz).isNotNull
        return clazz
    }
    catch(e: ClassNotFoundException) {
        return Assertions.fail<Nothing>("Class $className could not be loaded")
    }
}

/**
 * Returns the classpath for a dependency (format $name-$version).
 * This is necessary to know the actual location of a dependency
 * which has been included in test runtime (build.gradle).
 */
fun classpathOf(dependency: String): File {
    val regex = Regex(".*$dependency\\.jar")
    return ClassGraph().classpathFiles.first { classpath -> classpath.name.matches(regex) }
}
