package com.tschuchort.compiletesting

import java.io.File
import java.net.URLClassLoader

/** Result of the compilation */
class Result(
    val exitCode: ExitCode, val outputDirectory: File,
    /** Messages that were printed by the compilation */
    val messages: String
) {
    /** All output files that were created by the compilation */
    val generatedFiles: Collection<File> = outputDirectory.listFilesRecursively()

    /** class loader to load the compile classes */
    val classLoader = URLClassLoader(
        arrayOf(outputDirectory.toURI().toURL()),
        this::class.java.classLoader
    )
}