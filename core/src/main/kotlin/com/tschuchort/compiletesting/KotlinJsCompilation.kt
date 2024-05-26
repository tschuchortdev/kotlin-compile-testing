package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.*

@ExperimentalCompilerApi
@Suppress("MemberVisibilityCanBePrivate")
class KotlinJsCompilation : AbstractKotlinCompilation<K2JSCompilerArguments>() {

  var outputFileName: String = "test.js"

  /**
   * Generate unpacked KLIB into parent directory of output JS file. In combination with -meta-info
   * generates both IR and pre-IR versions of library.
   */
  var irProduceKlibDir: Boolean = false

  /** Generate packed klib into file specified by -output. Disables pre-IR backend */
  var irProduceKlibFile: Boolean = false

  /** Generates JS file using IR backend. Also disables pre-IR backend */
  var irProduceJs: Boolean = true

  /** Perform experimental dead code elimination */
  var irDce: Boolean = false

  /** Print declarations' reachability info to stdout during performing DCE */
  var irDcePrintReachabilityInfo: Boolean = false

  /** Disables pre-IR backend */
  var irOnly: Boolean = true

  /** Specify a compilation module name for IR backend */
  var irModuleName: String? = null

  /** Base name of generated files */
  var moduleName: String? = null

  /**
   * Path to the kotlin-stdlib-js.jar
   * If none is given, it will be searched for in the host
   * process' classpaths
   */
  var kotlinStdLibJsJar: File? by default {
    HostEnvironment.kotlinStdLibJsJar
  }

  /**
   * Path to the kotlin-dom-api-compat.klib
   * If none is given, it will be searched for in the host
   * process' classpaths
   */
  var kotlinStdLibDomApi: File? by default {
    HostEnvironment.kotlinDomApiCompatKlib
  }

  /**
   * Generate TypeScript declarations .d.ts file alongside JS file. Available in IR backend only
   */
  var generateDts: Boolean = false

  // *.class files, Jars and resources (non-temporary) that are created by the
  // compilation will land here
  val outputDir get() = workingDir.resolve("output")

  // setup common arguments for the two kotlinc calls
  private fun jsArgs() = commonArguments(K2JSCompilerArguments()) { args ->
    // the compiler should never look for stdlib or reflect in the
    // kotlinHome directory (which is null anyway). We will put them
    // in the classpath manually if they're needed
    args.noStdlib = true

    args.moduleKind = "commonjs"
    args.outputFile = File(outputDir, outputFileName).absolutePath
    args.outputDir = outputDir.absolutePath
    args.sourceMapBaseDirs = jsClasspath().joinToString(separator = File.pathSeparator)
    args.libraries = listOfNotNull(kotlinStdLibJsJar, kotlinStdLibDomApi).joinToString(separator = File.pathSeparator)

    args.irProduceKlibDir = irProduceKlibDir
    args.irProduceKlibFile = irProduceKlibFile
    args.irProduceJs = irProduceJs
    args.irDce = irDce
    args.irDcePrintReachabilityInfo = irDcePrintReachabilityInfo
    args.irOnly = irOnly
    args.moduleName = moduleName
    args.irModuleName = irModuleName
    args.generateDts = generateDts
  }

  /** Runs the compilation task */
  fun compile(): JsCompilationResult {
    // make sure all needed directories exist
    sourcesDir.mkdirs()
    outputDir.mkdirs()

    // write given sources to working directory
    val sourceFiles = sources.map { it.writeIfNeeded(sourcesDir) }

    pluginClasspaths.forEach { filepath ->
      if (!filepath.exists()) {
        error("Plugin $filepath not found")
        return makeResult(KotlinCompilation.ExitCode.INTERNAL_ERROR)
      }
    }


    /* Work around for warning that sometimes happens:
    "Failed to initialize native filesystem for Windows
    java.lang.RuntimeException: Could not find installation home path.
    Please make sure bin/idea.properties is present in the installation directory"
    See: https://github.com/arturbosch/detekt/issues/630
    */
    withSystemProperty("idea.use.native.fs.for.win", "false") {
      // step 1: compile Kotlin files
      return makeResult(compileKotlin(sourceFiles, K2JSCompiler(), jsArgs()))
    }
  }

  private fun makeResult(exitCode: KotlinCompilation.ExitCode): JsCompilationResult {
    val messages = internalMessageBuffer.readUtf8()

    if (exitCode != KotlinCompilation.ExitCode.OK)
      searchSystemOutForKnownErrors(messages)

    return JsCompilationResult(exitCode, messages, this)
  }

  private fun jsClasspath() = mutableListOf<File>().apply {
    addAll(classpaths)
    addAll(listOfNotNull(kotlinStdLibCommonJar, kotlinStdLibJsJar))

    if (inheritClassPath) {
      addAll(hostClasspaths)
      log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
    }
  }.distinct()
}
