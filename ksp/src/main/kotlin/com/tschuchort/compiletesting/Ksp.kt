/**
 * Adds support for KSP (https://goo.gle/ksp).
 */
package com.tschuchort.compiletesting

import com.google.devtools.ksp.AbstractKotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
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
 * The directory where generated KSP sources are written
 */
val KotlinCompilation.kspSourcesDir: File
    get() = kspWorkingDir.resolve("sources")

/**
 * Arbitrary arguments to be passed to ksp
 */
var KotlinCompilation.kspArgs: MutableMap<String, String>
    get() = getKspRegistrar().options
    set(value) {
        val registrar = getKspRegistrar()
        registrar.options = value
    }

/**
 * Controls for enabling incremental processing in KSP.
 */
var KotlinCompilation.kspIncremental: Boolean
    get() = getKspRegistrar().incremental
    set(value) {
        val registrar = getKspRegistrar()
        registrar.incremental = value
    }

/**
 * Controls for enabling incremental processing logs in KSP.
 */
var KotlinCompilation.kspIncrementalLog: Boolean
    get() = getKspRegistrar().incrementalLog
    set(value) {
        val registrar = getKspRegistrar()
        registrar.incrementalLog = value
    }

private val KotlinCompilation.kspJavaSourceDir: File
    get() = kspSourcesDir.resolve("java")

private val KotlinCompilation.kspKotlinSourceDir: File
    get() = kspSourcesDir.resolve("kotlin")

private val KotlinCompilation.kspResourceDir: File
    get() = kspSourcesDir.resolve("resource")

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
 * The directory where compiled KSP caches are written
 */
private val KotlinCompilation.kspCachesDir: File
    get() = kspWorkingDir.resolve("caches")

/**
 * Custom subclass of [AbstractKotlinSymbolProcessingExtension] where processors are pre-defined instead of being
 * loaded via ServiceLocator.
 */
private class KspTestExtension(
    options: KspOptions,
    private val processors: List<SymbolProcessor>,
    logger: KSPLogger
) : AbstractKotlinSymbolProcessingExtension(
    options = options,
    logger = logger,
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

    var options: MutableMap<String, String> = mutableMapOf()

    var incremental: Boolean = false
    var incrementalLog: Boolean = false

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        if (processors.isEmpty()) {
            return
        }
        val options = KspOptions.Builder().apply {
            this.projectBaseDir = compilation.kspWorkingDir

            this.processingOptions.putAll(compilation.kspArgs)

            this.incremental = this@KspCompileTestingComponentRegistrar.incremental
            this.incrementalLog = this@KspCompileTestingComponentRegistrar.incrementalLog

            this.cachesDir = compilation.kspCachesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.kspOutputDir = compilation.kspSourcesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.classOutputDir = compilation.kspClassesDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.javaOutputDir = compilation.kspJavaSourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.kotlinOutputDir = compilation.kspKotlinSourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
            this.resourceOutputDir = compilation.kspResourceDir.also {
                it.deleteRecursively()
                it.mkdirs()
            }
        }.build()

        // Temporary until friend-paths is fully supported https://youtrack.jetbrains.com/issue/KT-34102
        @Suppress("invisible_member")
        val messageCollectorBasedKSPLogger = MessageCollectorBasedKSPLogger(
            PrintingMessageCollector(
                compilation.internalMessageStreamAccess,
                MessageRenderer.GRADLE_STYLE,
                compilation.verbose
            )
        )
        val registrar = KspTestExtension(options, processors, messageCollectorBasedKSPLogger)
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
