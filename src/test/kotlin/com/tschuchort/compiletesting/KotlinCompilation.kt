/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tschuchort.compiletesting

import io.github.classgraph.ClassGraph
import okio.Buffer
import okio.buffer
import okio.sink
import kotlin.reflect.KClass
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.*
import java.lang.RuntimeException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.lang.model.SourceVersion
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider


@Suppress("unused", "MemberVisibilityCanBePrivate")
data class KotlinCompilation(
	/** Working directory for the compilation */
	var workingDir: File,
	/**
	 * Additional arguments to be passed to kotlinc
	 *
	 * Options and their parameters that would usually be separated
	 * by whitespace in the CLI need to be passed in separate strings.
	 * */
	var addKotlincArgs: List<String> = emptyList(),
	/**
	 * Additional arguments to be passed to javac
	 *
	 * Options and their parameters that would usually be separated
	 * by whitespace in the CLI need to be passed in separate strings.
	 * */
	var addJavacArgs: List<String> = emptyList(),
	/** Arbitrary arguments to be passed to kapt */
	var kaptArgs: Map<String, String> = emptyMap(),
	/**
	 * Paths to directories or .jar files that contain classes
	 * to be made available in the compilation (i.e. added to
	 * the classpath)
	 */
	var classpaths: List<File> = emptyList(),
	/** Source files to be compiled */
	var sources: List<KotlinCompilation.SourceFile> = emptyList(),
	/** Services to be passed to kapt */
	var services: List<KotlinCompilation.Service<*, *>> = emptyList(),
	/**
	 * Path to the JDK to be used
	 *
     * If null, no JDK will be used with kotlinc (option -no-jdk)
     * and the system java compiler will be used with empty bootclasspath
     * (on JDK8) or --system none (on JDK9+). This can be useful if all
     * the JDK classes you need are already on the (inherited) classpath.
	 * */
	var jdkHome: File? = null,
	/**
	 * Helpful information (if [verbose] = true) and the compiler
	 * system output will be written to this stream
	 */
	var systemOut: PrintStream = PrintStream(NullStream),
	/**
	 * Path to the kotlin-stdlib.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinStdLibJar: File? = KotlinCompilation.findKtStdLib(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-stdlib-jdk*.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinStdLibJdkJar: File? = KotlinCompilation.findKtStdLibJdk(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-reflect.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinReflectJar: File? = KotlinCompilation.findKtReflect(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-script-runtime.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinScriptRuntimeJar: File? = KotlinCompilation.findKtScriptRt(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-stdlib-common.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	var kotlinStdLibCommonJar: File? = KotlinCompilation.findKtStdLibCommon(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the tools.jar file needed for kapt when using a JDK 8.
	 *
	 * Note: Using a tools.jar file with a JDK 9 or later leads to an
	 * internal compiler error!
	 */
	var toolsJar: File? = KotlinCompilation.findToolsInHostClasspath(
		log = { if (verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-annotation-processing-embeddable*.jar that
	 * contains kapt3.
	 *
	 * Only needed when [services] is not empty.
	 */
	var kapt3Jar: File? = KotlinCompilation.findKapt3(
		log = { if (verbose) systemOut.log(it) }
	),
	/** Inherit classpath from calling process */
	var inheritClassPath: Boolean = false,
	var jvmTarget: String? = null,
	var correctErrorTypes: Boolean = true,
	var skipRuntimeVersionCheck: Boolean = false,
	var verbose: Boolean = false,
	var suppressWarnings: Boolean = false,
	var allWarningsAsErrors: Boolean = false,
	var reportOutputFiles: Boolean = false,
	var reportPerformance: Boolean = false,
	var loadBuiltInsFromDependencies: Boolean = false
) {

	// Directory for input source files
	private val sourcesDir get() = File(workingDir, "sources")

	// *.class files, Jars and resources (non-temporary) that are created by the
	// compilation will land here
	val classesDir get() = File(workingDir, "classes")

	// Java annotation processors that are compile by kapt will put their generated files here
	val kaptSourceDir get() = File(workingDir, "kapt/sources")

	// Output directory for Kotlin source files generated by kapt
	val kaptKotlinGeneratedDir get() = kaptArgs[OPTION_KAPT_KOTLIN_GENERATED]
		?.let { path ->
			require(File(path).isDirectory) { "$OPTION_KAPT_KOTLIN_GENERATED must be a directory" }
			File(path)
		}
		?: File(workingDir, "kapt/kotlinGenerated")


	/**
	 * Generate a .jar file that holds ServiceManager registrations. Necessary because AutoService's
	 * results might not be visible to this test.
	 */
	private fun writeServicesJar() = File(workingDir, "services.jar").apply {
		val servicesGroupedByClass = services.groupBy({ it.serviceClass }, { it.implementationClass })

		ZipOutputStream(FileOutputStream(this)).use { zipOutputStream ->
			for (serviceEntry in servicesGroupedByClass) {
				zipOutputStream.putNextEntry(
					ZipEntry("META-INF/services/${serviceEntry.key.qualifiedName}")
				)
				val serviceFile = zipOutputStream.sink().buffer()
				for (implementation in serviceEntry.value) {
					serviceFile.writeUtf8(implementation.qualifiedName!!)
					serviceFile.writeUtf8("\n")
				}
				serviceFile.emit() // Don't close the entry; that closes the file.
				zipOutputStream.closeEntry()
			}
		}
	}

	/** A Kotlin source file to be compiled */
	data class SourceFile(val path: String, val contents: String) {
		/**
		 * Writes the source file to the location and returns the
		 * corresponding [File] object
		 */
		fun writeTo(dir: File): File =
			File(dir, path).apply {
				parentFile.mkdirs()
				sink().buffer().use {
					it.writeUtf8(contents)
				}
			}
	}

    /** ExitCode of the entire Kotlin compilation process */
    enum class ExitCode {
        OK, INTERNAL_ERROR, COMPILATION_ERROR, SCRIPT_EXECUTION_ERROR
    }

	/** Result of the compilation */
	data class Result(val exitCode: ExitCode, val outputDirectory: File) {
		val generatedFiles: Collection<File> = outputDirectory.listFilesRecursively()
	}

	/** A service that will be passed to kapt */
	data class Service<S : Any, T : S>(val serviceClass: KClass<S>, val implementationClass: KClass<T>)

	// setup common arguments for the two kotlinc calls
	private fun commonK2JVMArgs() = K2JVMCompilerArguments().also { it ->
		it.destination = classesDir.absolutePath
		it.classpath = allClasspaths().joinToString(separator = File.pathSeparator)

		if(jdkHome != null) {
			it.jdkHome = jdkHome!!.absolutePath
		}
		else {
			if(verbose)
				systemOut.log("Using option -no-jdk. Kotlinc won't look for a JDK.")

			it.noJdk = true
		}

		// the compiler should never look for stdlib or reflect in the
		// kotlinHome directory (which is null anyway). We will put them
		// in the classpath manually if they're needed
		it.noStdlib = true
		it.noReflect = true

		it.jvmTarget = jvmTarget
		it.verbose = verbose
		it.skipRuntimeVersionCheck = skipRuntimeVersionCheck
		it.suppressWarnings = suppressWarnings
		it.allWarningsAsErrors = allWarningsAsErrors
		it.reportOutputFiles = reportOutputFiles
		it.reportPerf = reportPerformance
		it.reportOutputFiles = reportOutputFiles
		it.loadBuiltInsFromDependencies = loadBuiltInsFromDependencies
	}

	/** Performs the 1st and 2nd compilation step to generate stubs and run annotation processors */
	private fun stubsAndApt(messageCollector: PrintingMessageCollector): ExitCode {
		// stubs and incrementalData are temporary
		val kaptStubsDir = File(workingDir, "kapt/stubs").apply { mkdirs() }
		val kaptIncrementalDataDir = File(workingDir, "kapt/incrementalData").apply { mkdirs() }

		require(kapt3Jar != null) { "kapt3Jar has to be non-null if annotation processing is used" }

		val kaptPluginClassPaths = listOfNotNull(kapt3Jar!!.absolutePath, toolsJar?.absolutePath).toTypedArray()

		val encodedKaptArgs = encodeOptionsForKapt(kaptArgs.toMutableMap().apply {
			putIfAbsent(OPTION_KAPT_KOTLIN_GENERATED, kaptKotlinGeneratedDir.absolutePath)
		})

		val kaptPluginOptions = arrayOf(
			"plugin:org.jetbrains.kotlin.kapt3:sources=${kaptSourceDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:classes=${classesDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:stubs=${kaptStubsDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:incrementalData=${kaptIncrementalDataDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:apclasspath=${writeServicesJar().absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=$correctErrorTypes",
			// Don't forget aptMode! Without it, the compiler will crash with an obscure error about
			// write unsafe context
			"plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt",
			"plugin:org.jetbrains.kotlin.kapt3:mapDiagnosticLocations=true",
			*if (verbose)
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:verbose=true")
			else
				emptyArray(),
			*if (kaptArgs.isNotEmpty())
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:apoptions=$encodedKaptArgs")
			else
				emptyArray()
		)
		
		val k2JvmArgs = commonK2JVMArgs().also {
			it.freeArgs = sourcesDir.listFilesRecursively()
				.map(File::getAbsolutePath).distinct() + addKotlincArgs

			it.pluginOptions = if(it.pluginOptions != null)
				it.pluginOptions!!.plus(kaptPluginOptions)
			else
				kaptPluginOptions

			it.pluginClasspaths = if(it.pluginClasspaths != null)
				it.pluginClasspaths!!.plus(kaptPluginClassPaths)
			else
				kaptPluginClassPaths
		}

		return convertKotlinExitCode(
            K2JVMCompiler().execImpl(messageCollector, Services.EMPTY, k2JvmArgs))
	}

	/** Performs the 3rd compilation step to compile Kotlin source files */
	private fun compileKotlin(messageCollector: PrintingMessageCollector): ExitCode {
		val sources = (sourcesDir.listFilesRecursively() +
				kaptKotlinGeneratedDir.listFilesRecursively() +
				kaptSourceDir.listFilesRecursively())

		if(sources.filter<File>(File::isKotlinFile).isEmpty())
			return ExitCode.OK

		// in this step also include source files generated by kapt in the previous step
		val k2JvmArgs = commonK2JVMArgs().also {
			it.freeArgs = sources.map(File::getAbsolutePath)
				.distinct() + addKotlincArgs
		}

        return convertKotlinExitCode(
            K2JVMCompiler().execImpl(messageCollector, Services.EMPTY, k2JvmArgs))
	}

	/**
	 * 	Base javac arguments that only depend on the the arguments given by the user
	 *  Depending on which compiler implementation is actually used, more arguments
	 *  may be added
	 */
	private fun baseJavacArgs(isJavac9OrLater: Boolean) = mutableListOf<String>().apply {
		if(verbose) {
			add("-verbose")
			add("-Xlint:path") // warn about invalid paths in CLI
			add("-Xlint:options") // warn about invalid options in CLI

			if(isJavac9OrLater)
				add("-Xlint:module") // warn about issues with the module system
		}

		addAll("-d", classesDir.absolutePath)

		add("-proc:none") // disable annotation processing

		if(allWarningsAsErrors)
			add("-Werror")

		// also add class output path to javac classpath so it can discover
		// already compiled Kotlin classes
		addAll("-cp", (allClasspaths() + classesDir)
			.joinToString(File.pathSeparator, transform = File::getAbsolutePath))

		addAll(addJavacArgs)
	}


	/** Performs the 4th compilation step to compile Java source files */
	private fun compileJava(): ExitCode {
		val javaSources = (sourcesDir.listFilesRecursively() + kaptSourceDir.listFilesRecursively())
			.filterNot<File>(File::isKotlinFile)

		if(javaSources.isEmpty())
			return ExitCode.OK

        if(jdkHome != null) {
            /* If a JDK home is given, try to run javac from there so it uses the same JDK
               as K2JVMCompiler. Changing the JDK of the system java compiler via the
               "--system" and "-bootclasspath" options is not so easy. */

            val jdkBinFile = File(jdkHome, "bin")
            check(jdkBinFile.exists()) { "No JDK bin folder found at: ${jdkBinFile.toPath()}" }

			val javacCommand = jdkBinFile.absolutePath + File.separatorChar + "javac"

			val isJavac9OrLater = isJavac9OrLater(getJavacVersionString(javacCommand))
			val javacArgs = baseJavacArgs(isJavac9OrLater)

            val javacProc = ProcessBuilder(listOf(javacCommand) + javacArgs + javaSources.map(File::getAbsolutePath))
                .directory(workingDir)
                .redirectErrorStream(true)
				.start()

			javacProc.inputStream.copyTo(systemOut)
			javacProc.errorStream.copyTo(systemOut)

            return when(javacProc.waitFor()) {
                0 -> ExitCode.OK
                1 -> ExitCode.COMPILATION_ERROR
                else -> ExitCode.INTERNAL_ERROR
            }
        }
        else {
            /*  If no JDK is given, we will use the host process' system java compiler
                and erase the bootclasspath. The user is then on their own to somehow
                provide the JDK classes via the regular classpath because javac won't
                work at all without them */

			val isJavac9OrLater = isJdk9OrLater()
			val javacArgs = baseJavacArgs(isJavac9OrLater).apply {
				// erase bootclasspath or JDK path because no JDK was specified
				if (isJavac9OrLater)
					addAll("--system", "none")
				else
					addAll("-bootclasspath", "")
			}

            if(verbose)
                systemOut.log("jdkHome is null. Using system java compiler of the host process.")

            val javac = ToolProvider.getSystemJavaCompiler()
            check(javac != null) { "System java compiler is null! Are you running without JDK?" }

            val javaFileManager = javac.getStandardFileManager(null, null, null)

            val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

            fun printDiagnostics() = diagnosticCollector.diagnostics.forEach { diag ->
                when(diag.kind) {
                    Diagnostic.Kind.ERROR -> systemOut.error(diag.getMessage(null))
                    Diagnostic.Kind.WARNING,
                    Diagnostic.Kind.MANDATORY_WARNING -> systemOut.warning(diag.getMessage(null))
                    else -> if(verbose) systemOut.log(diag.getMessage(null))
                }
            }

            try {
                val noErrors = javac.getTask(
                    OutputStreamWriter(systemOut), javaFileManager,
                    diagnosticCollector, javacArgs,
                    /* classes to be annotation processed */ null,
					javaSources.map { FileJavaFileObject(it) }
						.filter { it.kind == JavaFileObject.Kind.SOURCE }
                ).call()

                printDiagnostics()

                return if(noErrors)
                    ExitCode.OK
                else
                    ExitCode.COMPILATION_ERROR
            }
            catch (e: Exception) {
                if(e is RuntimeException || e is IllegalArgumentException) {
                    printDiagnostics()
                    systemOut.error(e.toString())
                    return ExitCode.INTERNAL_ERROR
                }
                else
                    throw e
            }
        }
	}

	/** Runs the compilation task */
	fun compile(): Result {
		// make sure all needed directories exist
		sourcesDir.mkdirs()
		classesDir.mkdirs()
		kaptSourceDir.mkdirs()
		kaptKotlinGeneratedDir.mkdirs()

		// write given sources to working directory
		sources.forEach { it.writeTo(sourcesDir) }

		// Buffer for capturing compiler's logging output
		val compilerSystemOutBuffer = Buffer()
		val compilerMessageCollector = PrintingMessageCollector(
			PrintStream(TeeOutputStream(systemOut, compilerSystemOutBuffer.outputStream())),
			MessageRenderer.WITHOUT_PATHS, true)

		/*
		There are 4 steps to the compilation process:
		1. Generate stubs (using kotlinc with kapt plugin which does no further compilation)
		2. Run apt (using kotlinc with kapt plugin which does no further compilation)
		3. Run kotlinc with the normal Kotlin sources and Kotlin sources generated in step 2
		4. Run javac with Java sources and the compiled Kotlin classes
		 */

		// step 1 and 2: generate stubs and compile annotation processors
		if(services.isNotEmpty()) {
			val exitCode = stubsAndApt(compilerMessageCollector)
			if(exitCode != ExitCode.OK) {
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())
				return Result(exitCode, classesDir)
			}
		}
		else if(verbose) {
			systemOut.log("No services were given. Not running kapt steps.")
		}

		// step 3: compile Kotlin files
		compileKotlin(compilerMessageCollector).let { exitCode ->
			if(exitCode != ExitCode.OK) {
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())
				return Result(exitCode, classesDir)
			}
		}

		// step 4: compile Java files
		compileJava().let { exitCode ->
			if(exitCode != ExitCode.OK)
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())

			return Result(exitCode, classesDir)
		}
	}

	private fun allClasspaths() = mutableListOf<File>().apply {
		addAll(classpaths)

		addAll(listOfNotNull(kotlinStdLibJar, kotlinReflectJar, kotlinScriptRuntimeJar))

		if(inheritClassPath) {
			val hostClasspaths = getHostClasspaths()
			addAll(hostClasspaths)

			if(verbose)
				systemOut.log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
		}
	}.distinct()

	/** Searches compiler log for known errors that are hard to debug for the user */
	private fun searchSystemOutForKnownErrors(compilerSystemOut: String) {
		if(compilerSystemOut.contains("No enum constant com.sun.tools.javac.main.Option.BOOT_CLASS_PATH")) {

			systemOut.warning(
				"${this::class.simpleName} has detected that the compilation failed with an error that may be " +
						"caused by including a tools.jar file together with a JDK of version 9 or later. " +
						if (inheritClassPath)
							"Make sure that no tools.jar (or unwanted JDK) is in the inherited classpath"
						else ""
			)
		}
	}

    companion object {
		const val OPTION_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"

		/** Tries to find a file matching the given [regex] in the host process' classpath */
		private fun findInHostClasspath(shortName: String, regex: Regex, log: ((String) -> Unit)? = null): File? {
			val jarFile = getHostClasspaths()
				.firstOrNull { classpath ->
				classpath.name.matches(regex)
				//TODO("check that jar file actually contains the right classes")
			}

			if(jarFile == null && log != null)
				log("Searched classpath for $shortName but didn't find anything.")
			else if(log != null)
				log("Searched classpath for $shortName and found: $jarFile")

			return jarFile
		}

		/** Tries to find the kotlin-stdlib.jar in the host process' classpath */
		fun findKtStdLib(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-stdlib.jar",
			Regex("(kotlin-stdlib|kotlin-runtime)(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log
		)
			// kotlin-stdlib.jar used to be kotlin-runtime.jar in <1.1

		/** Tries to find the kotlin-stdlib-jdk*.jar in the host process' classpath */
		fun findKtStdLibJdk(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-stdlib-jdk*.jar",
			Regex("kotlin-stdlib-jdk[0-9]+(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log
		)

		/** Tries to find the kotlin-stdlib-common.jar in the host process' classpath */
		fun findKtStdLibCommon(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-stdlib-common.jar",
			Regex("kotlin-stdlib-common(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log
		)

		/** Tries to find the kotlin-reflect.jar in the host process' classpath */
		fun findKtReflect(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-reflect.jar",
			Regex("kotlin-reflect(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log
		)

		/** Tries to find the kotlin-script-runtime.jar in the host process' classpath */
		fun findKtScriptRt(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-script-runtime.jar",
			Regex("kotlin-script-runtime(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log
		)

        /** Tries to find the kapt 3 jar in the host process' classpath */
        fun findKapt3(log: ((String) -> Unit)? = null)
				= findInHostClasspath(
			"kotlin-annotation-processing(-embeddable).jar",
			Regex("kotlin-annotation-processing(-(embeddable|gradle|maven))?(-[0-9]+\\.[0-9]+\\.[0-9]+)?\\.jar"), log
		)


        /** Tries to find the tools.jar needed for kapt in the host process' classpath */
        fun findToolsInHostClasspath(log: ((String) -> Unit)? = null)
				= findInHostClasspath("tools.jar", Regex("tools.jar"), log
		)

        /** Returns the files on the classloader's classpath and modulepath */
        fun getHostClasspaths(): List<File> {
			val classGraph = ClassGraph()
				.enableSystemJarsAndModules()
				.removeTemporaryFilesAfterScan()

			val classpaths = classGraph.classpathFiles
			val modules = classGraph.modules.mapNotNull { it.locationFile }

			return (classpaths + modules).distinctBy(File::getAbsolutePath)
        }

        /** Finds the tools.jar given a path to a JDK 8 or earlier */
        fun findToolsJarFromJdk(jdkHome: File): File
                = File(jdkHome.absolutePath + "/../lib/tools.jar").also { check(it.isFile) }

        private fun PrintStream.log(s: String) = println("logging: $s")
        private fun PrintStream.warning(s: String) = println("warning: $s")
        private fun PrintStream.error(s: String) = println("error: $s")
    }
}

/**
 * Base64 encodes a mapping of annotation processor addKotlincArgs for kapt, as specified by
 * https://kotlinlang.org/docs/reference/kapt.html#apjavac-options-encoding
 */
private fun encodeOptionsForKapt(options: Map<String, String>): String {
	val buffer = Buffer()
	ObjectOutputStream(buffer.outputStream()).use { oos ->
		oos.writeInt(options.size)
		for ((key, value) in options.entries) {
			oos.writeUTF(key)
			oos.writeUTF(value)
		}
	}
	return buffer.readByteString().base64()
}


private fun convertKotlinExitCode(code: ExitCode) = when(code) {
    ExitCode.OK -> KotlinCompilation.ExitCode.OK
    ExitCode.INTERNAL_ERROR -> KotlinCompilation.ExitCode.INTERNAL_ERROR
    ExitCode.COMPILATION_ERROR -> KotlinCompilation.ExitCode.COMPILATION_ERROR
    ExitCode.SCRIPT_EXECUTION_ERROR -> KotlinCompilation.ExitCode.SCRIPT_EXECUTION_ERROR
}


