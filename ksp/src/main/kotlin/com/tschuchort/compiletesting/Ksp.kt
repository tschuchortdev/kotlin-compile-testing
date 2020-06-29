/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingCommandLineProcessor
import org.jetbrains.kotlin.ksp.KotlinSymbolProcessingComponentRegistrar
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import java.io.File

private const val KSP_PLUGIN_ID = "org.jetbrains.kotlin.ksp"

private fun KotlinCompilation.initAndGetKspConfig(): KspConfiguration {
    val config = KspConfiguration(workingDir.resolve("ksp"))
    if (config.workingDir.exists()) {
        // already configured, just return
        return config
    }
    config.classesOutDir.mkdirs()
    config.sourcesOurDir.mkdirs()
    config.syntheticSources.mkdirs()
    val kspOptions = listOf(
        PluginOption(KSP_PLUGIN_ID, "apclasspath", config.syntheticSources.path),
        PluginOption(KSP_PLUGIN_ID, "classes", config.classesOutDir.path),
        PluginOption(KSP_PLUGIN_ID, "sources", config.sourcesOurDir.path)
    )
    compilerPlugins = compilerPlugins + KotlinSymbolProcessingComponentRegistrar()
    commandLineProcessors = commandLineProcessors + listOf(KotlinSymbolProcessingCommandLineProcessor())
    pluginOptions = pluginOptions + kspOptions
    return config
}

fun KotlinCompilation.symbolProcessors(
    vararg processors: Class<out SymbolProcessor>
) {
    check(processors.isNotEmpty()) {
        "Must provide at least 1 symbol processor"
    }
    val config = initAndGetKspConfig()

    // create a fake classpath that references our symbol processor
    config.syntheticSources.apply {
        resolve("META-INF/services/org.jetbrains.kotlin.ksp.processing.SymbolProcessor").apply {
            parentFile.mkdirs()
            // keep existing ones in case this function is called multiple times
            val existing = if (exists()) {
                readLines(Charsets.UTF_8)
            } else {
                emptyList()
            }
            val processorNames = existing + processors.map {
                it.typeName
            }
            writeText(
                processorNames.joinToString(System.lineSeparator())
            )
        }
    }
    this.kaptSourceDir
}

private data class KspConfiguration(
    val workingDir: File
) {
    val classesOutDir = workingDir.resolve("classesOutput")
    val sourcesOurDir = workingDir.resolve("sourcesOutput")
    val syntheticSources = workingDir.resolve("synthetic-ksp-service")
}