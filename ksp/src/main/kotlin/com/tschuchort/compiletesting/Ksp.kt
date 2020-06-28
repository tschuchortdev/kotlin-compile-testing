/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingCommandLineProcessor
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingComponentRegistrar
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor

private const val KSP_PLUGIN_ID = "org.jetbrains.kotlin.ksp"

// TODO can we add support for instances?
fun KotlinCompilation.symbolProcessor(
    vararg processors : Class<out SymbolProcessor>
) {
    check(processors.isNotEmpty()) {
        "Must provide at least 1 symbol processor"
    }
    val kspWorkingDir = workingDir.resolve("ksp")

    // create a fake classpath that references our symbol processor
    val processorClasspath = kspWorkingDir.resolve("synthetic-ksp-service").apply {
        resolve("META-INF/services/org.jetbrains.kotlin.ksp.processing.SymbolProcessor").apply {
            parentFile.mkdirs()
            val processorNames = processors.joinToString(System.lineSeparator()) {
                it.typeName
            }
            writeText(
                processorNames
            )
        }
    }

    val classesFolder = kspWorkingDir.resolve("outClasses").also {
        it.mkdirs()
    }
    val sourceFolder = kspWorkingDir.resolve("outSources").also {
        it.mkdirs()
    }
    val kspOptions = listOf(
        PluginOption(KSP_PLUGIN_ID, "apclasspath", processorClasspath.path),
        PluginOption(KSP_PLUGIN_ID, "classes", classesFolder.path),
        PluginOption(KSP_PLUGIN_ID, "sources", sourceFolder.path)
    )
    compilerPlugins += KotlinSymbolProcessingComponentRegistrar()
    commandLineProcessors += listOf(KotlinSymbolProcessingCommandLineProcessor())
    pluginOptions += kspOptions
    this.kaptSourceDir
}
