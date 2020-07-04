/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ksp.AbstractKotlinSymbolProcessingExtension
import org.jetbrains.kotlin.ksp.KspOptions
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File

/**
 * The list of symbol processors for the kotlin compilation.
 * https://goo.gle/ksp
 */
var KotlinCompilation.symbolProcessors: List<SymbolProcessor>
    get() = getKspRegistrar().processors
    set(value) {
        val registrar = getKspRegistrar()
        registrar.processors = value
    }

/**
 * The directory where generates KSP sources are written
 */
val KotlinCompilation.kspSourcesDir: File
    get() = kspWorkingDir.resolve("sources")

/**
 * The working directory for KSP
 */
private val KotlinCompilation.kspWorkingDir: File
    get() = workingDir.resolve("ksp")

/**
 * The directory where compiled KSP classes are written
 */
// TODO this seems to be ignored by KSP and it is putting classes into regular classes directory
//  but we still need to provide it in the KSP options builder as it is required
//  once it works, we should make the property public.
private val KotlinCompilation.kspClassesDir: File
    get() = kspWorkingDir.resolve("classes")

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined instead of being
 * loaded via ServiceLocator.
 */
private class KspTestExtension(
    options: KspOptions,
    private val processors: List<SymbolProcessor>
) : AbstractKotlinSymbolProcessingExtension(
    options = options,
    testMode = false
) {
    override fun loadProcessors() = processors
}

/**
 * Registers the [KspTestExtension] to load the given list of processors.
 */
private class KspCompileTestingComponentRegistrar(
    private val compilation: KotlinCompilation
) : ComponentRegistrar {
    var processors = emptyList<SymbolProcessor>()
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (processors.isEmpty()) {
            return
        }
        val options = KspOptions.Builder().apply {
            this.classesOutputDir = compilation.kspClassesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.sourcesOutputDir = compilation.kspSourcesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
        }.build()
        val registrar = KspTestExtension(options, processors)
        AnalysisHandlerExtension.registerExtension(project, registrar)
    }
}

/**
 * Gets the test registrar from the plugin list or adds if it does not exist.
 */
private fun KotlinCompilation.getKspRegistrar(): KspCompileTestingComponentRegistrar {
    compilerPlugins.firstIsInstanceOrNull<KspCompileTestingComponentRegistrar>()?.let {
        return it
    }
    val kspRegistrar = KspCompileTestingComponentRegistrar(this)
    compilerPlugins = compilerPlugins + kspRegistrar
    return kspRegistrar
}
