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
package com.tschuchort.compiletest

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
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.lang.model.SourceVersion
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class KotlinCompilation(
	/** Working directory for the compilation */
	val workingDir: File,
	/** Free arguments to be passed to kotlinc */
	val freeArgs: List<String> = emptyList(),
	/** Arbitrary arguments to be passed to kapt */
	kaptArgs: Map<String, String> = emptyMap(),
	/**
	 * Paths to directories or .jar files that contain classes
	 * to be made available in the compilation (i.e. added to
	 * the classpath)
	 */
	classpaths: List<File> = emptyList(),
	/** Source files to be compiled */
	val sources: List<SourceFile> = emptyList(),
	/** Services to be passed to kapt */
	val services: List<Service<*, *>> = emptyList(),
	/**
	 * Path to the JDK to be used
	 *
	 * null if no JDK is to be used (option -no-jdk)
	 * */
	val jdkHome: File? = null,
	/**
	 * Path to the kotlin-stdlib.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	val kotlinStdLibJar: File? = findKtStdLib(
		log = { if(verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-stdlib-jdk*.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	val kotlinStdLibJdkJar: File? = findKtStdLibJdk(
		log = { if(verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-reflect.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	val kotlinReflectJar: File? = findKtReflect(
		log = { if(verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-script-runtime.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	val kotlinScriptRuntimeJar: File? = findKtScriptRt(
		log = { if(verbose) systemOut.log(it) }
	),
	/**
	 * Path to the kotlin-stdlib-common.jar
	 * If none is given, it will be searched for in the host
	 * process' classpaths
	 */
	val kotlinStdLibCommonJar: File? = findKtStdLibCommon(
		log = { if(verbose) systemOut.log(it) }
	),
	/**
	 * Path to the tools.jar file needed for kapt when using a JDK 8.
	 *
	 * Note: Using a tools.jar file with a JDK 9 or later leads to an
	 * internal compiler error!
	 */
	val toolsJar: File? = findToolsInHostClasspath(
        log = { if(verbose) systemOut.log(it) }
    ),
	/**
     * Path to the kotlin-annotation-processing-embeddable*.jar that
     * contains kapt3.
     *
     * Only needed when [services] is not empty.
     */
    val kapt3Jar: File? = findKapt3(
		log = { if(verbose) systemOut.log(it) }
    ),
	/** Inherit classpath from calling process */
	val inheritClassPath: Boolean = false,
	val jvmTarget: String? = null,
	val correctErrorTypes: Boolean = true,
	val skipRuntimeVersionCheck: Boolean = false,
	val verbose: Boolean = false,
	val suppressWarnings: Boolean = false,
	val allWarningsAsErrors: Boolean = false,
	val reportOutputFiles: Boolean = false,
	val reportPerformance: Boolean = false,
	val loadBuiltInsFromDependencies: Boolean = false,
	/**
	 * Helpful information (if [verbose] = true) and the compiler
	 * system output will be written to this stream
	 */
	val systemOut: PrintStream = PrintStream(NullStream)
) {
	val kaptArgs: Map<String, String> = kaptArgs.toMutableMap().apply {
		putIfAbsent(OPTION_KAPT_KOTLIN_GENERATED, File(workingDir, "kapt/kotlinGenerated").absolutePath)
	}

	// Directory for input source files
	val sourcesDir = File(workingDir, "sources").apply { mkdirs() }

	// *.class files, Jars and resources (non-temporary) that are created by the
	// compilation will land here
	val classesDir = File(workingDir, "classes").apply { mkdirs() }

	// Java annotation processors that are run by kapt will put their generated files here
	val kaptSourceDir = File(workingDir, "kapt/sources").apply { mkdirs() }

	// Output directory for Kotlin source files generated by kapt
	val kaptKotlinGeneratedDir get() = File(kaptArgs[OPTION_KAPT_KOTLIN_GENERATED]).apply {
		require(isDirectory) { "$OPTION_KAPT_KOTLIN_GENERATED must be a directory" }
		mkdirs()
	}

	/**
	 * Generate a .jar file that holds ServiceManager registrations. Necessary because AutoService's
	 * results might not be visible to this test.
	 */
	val servicesJar = File(workingDir, "services.jar").apply {
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

	init {
		// write given sources to working directory
		sources.forEach { it.writeTo(sourcesDir) }
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

	/** Result of the compilation */
	data class Result(val exitCode: ExitCode, val generatedFiles: Collection<File>)

	/** A service that will be passed to kapt */
	data class Service<S : Any, T : S>(val serviceClass: KClass<S>, val implementationClass: KClass<T>)

	val allClasspaths: List<String> = mutableListOf<String>().apply {
		addAll(classpaths.map(File::getAbsolutePath))

		addAll(listOfNotNull(kotlinStdLibJar, kotlinReflectJar, kotlinScriptRuntimeJar)
			.map(File::getAbsolutePath))

		if(inheritClassPath) {
			val hostClasspaths = getHostClasspaths().map(File::getAbsolutePath)
			addAll(hostClasspaths)

			if(verbose)
				systemOut.log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
		}
	}.distinct()


	// setup common arguments for the two kotlinc calls
	protected open fun parseK2JVMArgs() = K2JVMCompilerArguments().also { it ->
		it.destination = classesDir.absolutePath
		it.classpath = allClasspaths.joinToString(separator = File.pathSeparator)

		if(jdkHome != null) {
			it.jdkHome = jdkHome.absolutePath
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

		val kaptPluginClassPaths = listOfNotNull(kapt3Jar.absolutePath, toolsJar?.absolutePath).toTypedArray()

		val kaptPluginOptions = arrayOf(
			"plugin:org.jetbrains.kotlin.kapt3:sources=${kaptSourceDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:classes=${classesDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:stubs=${kaptStubsDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:incrementalData=${kaptIncrementalDataDir.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:apclasspath=${servicesJar.absolutePath}",
			"plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=$correctErrorTypes",
			// Don't forget aptMode! Without it, the compiler will crash with an obscure error about
			// write unsafe context
			"plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt",
			*if (verbose)
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:verbose=true")
			else
				emptyArray(),
			*if (kaptArgs.isNotEmpty())
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:apoptions=${encodeOptionsForKapt(kaptArgs)}")
			else
				emptyArray()
		)
		
		val k2JvmArgs = parseK2JVMArgs().also {
			it.freeArgs = sourcesDir.listFilesRecursively()
				.map(File::getAbsolutePath).distinct() + freeArgs

			it.pluginOptions = if(it.pluginOptions != null)
				it.pluginOptions!!.plus(kaptPluginOptions)
			else
				kaptPluginOptions

			it.pluginClasspaths = if(it.pluginClasspaths != null)
				it.pluginClasspaths!!.plus(kaptPluginClassPaths)
			else
				kaptPluginClassPaths
		}

		return K2JVMCompiler().execImpl(messageCollector, Services.EMPTY, k2JvmArgs)
	}

	/** Performs the 3rd compilation step to compile Kotlin source files */
	private fun compileKotlin(messageCollector: PrintingMessageCollector): ExitCode {
		// in this step also include source files generated by kapt in the previous step
		val k2JvmArgs = parseK2JVMArgs().also {
			it.freeArgs = (sourcesDir.listFilesRecursively() +
					kaptKotlinGeneratedDir.listFilesRecursively() +
					kaptSourceDir.listFilesRecursively())
				.map(File::getAbsolutePath)
				.distinct() + freeArgs
		}

		return K2JVMCompiler().execImpl(messageCollector, Services.EMPTY, k2JvmArgs)
	}

	/** Performs the 4th compilation step to compile Java source files */
	private fun compileJava(): ExitCode {
		val javac = ToolProvider.getSystemJavaCompiler()
		val javaFileManager = javac.getStandardFileManager(null, null, null)

		val javaSources = sourcesDir.listFilesRecursively()
			.filterNot<File>(File::isKotlinFile)
			.map { FileJavaFileObject(it) }
			.filter { it.kind == JavaFileObject.Kind.SOURCE }

		val javacArgs = mutableListOf<String>().apply {
			if(verbose)
				add("-verbose")

			add("-d")
			add(classesDir.absolutePath)

			add("-proc:none") // disable annotation processing

			if(allWarningsAsErrors)
				add("-Werror")

			if(allClasspaths.isNotEmpty()) {
				add("-cp")
				// also add class output path to javac classpath so it can discover
				// already compiled Kotlin classes
				add((allClasspaths + classesDir.absolutePath).joinToString(File.pathSeparator))
			}
		}

		try {
			val noErrors = javac.getTask(
				OutputStreamWriter(systemOut), javaFileManager,
				/* diagnosticListener */ null, javacArgs,
				/* classes to be annotation processed */ null, javaSources
			).call()

			return if(noErrors)
				ExitCode.OK
			else
				ExitCode.COMPILATION_ERROR
		} catch (e: Exception) {
			if(e is RuntimeException || e is IllegalArgumentException) {
				systemOut.error(e.toString())
				return ExitCode.INTERNAL_ERROR
			}
			else
				throw e
		}
	}

	/** Runs the compilation task */
	fun run(): Result {
		val compilerSystemOutBuffer = Buffer()  // Buffer for capturing compiler's logging output
		val compilerMessageCollector = PrintingMessageCollector(
			PrintStream(
				TeeOutputStream(systemOut, compilerSystemOutBuffer.outputStream())),
			MessageRenderer.WITHOUT_PATHS, true)

		/*
		There are 4 steps to the compilation process:
		1. Generate stubs (using kotlinc with kapt plugin which does no further compilation)
		2. Run apt (using kotlinc with kapt plugin which does no further compilation)
		3. Run kotlinc with the normal Kotlin sources and Kotlin sources generated in step 2
		4. Run javac with Java sources and the compiled Kotlin classes
		 */

		// step 1 and 2: generate stubs and run annotation processors
		if(services.isNotEmpty()) {
			val exitCode = stubsAndApt(compilerMessageCollector)
			if(exitCode != ExitCode.OK) {
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())
				return Result(exitCode, classesDir.listFilesRecursively().toList())
			}
		}
		else if(verbose) {
			systemOut.log("No services were given. Not running kapt steps.")
		}

		// step 3: compile Kotlin files
		compileKotlin(compilerMessageCollector).let { exitCode ->
			if(exitCode != ExitCode.OK) {
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())
				return Result(exitCode, classesDir.listFilesRecursively().toList())
			}
		}

		// step 4: compile Java files
		compileJava().let { exitCode ->
			if(exitCode != ExitCode.OK) {
				searchSystemOutForKnownErrors(compilerSystemOutBuffer.readUtf8())
			}

			return Result(exitCode, classesDir.listFilesRecursively().toList())
		}
	}

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


	/**
	 * Base64 encodes a mapping of annotation processor freeArgs for kapt, as specified by
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

    companion object {
		const val OPTION_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"

		/** Tries to find a file matching the given [regex] in the host process' classpath */
		fun findInHostClasspath(shortName: String, regex: Regex, log: ((String) -> Unit)? = null): File? {
			val jarFile = getHostClasspaths().firstOrNull { classpath ->
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
				= findInHostClasspath("kotlin-stdlib.jar",
			Regex("(kotlin-stdlib|kotlin-runtime)(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log)
			// kotlin-stdlib.jar used to be kotlin-runtime.jar in <1.1

		/** Tries to find the kotlin-stdlib-jdk*.jar in the host process' classpath */
		fun findKtStdLibJdk(log: ((String) -> Unit)? = null)
				= findInHostClasspath("kotlin-stdlib-jdk*.jar",
			Regex("kotlin-stdlib-jdk[0-9]+(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log)

		/** Tries to find the kotlin-stdlib-common.jar in the host process' classpath */
		fun findKtStdLibCommon(log: ((String) -> Unit)? = null)
				= findInHostClasspath("kotlin-stdlib-common.jar",
			Regex("kotlin-stdlib-common(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log)

		/** Tries to find the kotlin-reflect.jar in the host process' classpath */
		fun findKtReflect(log: ((String) -> Unit)? = null)
				= findInHostClasspath("kotlin-reflect.jar",
			Regex("kotlin-reflect(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log)

		/** Tries to find the kotlin-script-runtime.jar in the host process' classpath */
		fun findKtScriptRt(log: ((String) -> Unit)? = null)
				= findInHostClasspath("kotlin-script-runtime.jar",
			Regex("kotlin-script-runtime(-[0-9]+\\.[0-9]+\\.[0-9]+)\\.jar"), log)

        /** Tries to find the kapt 3 jar in the host process' classpath */
        fun findKapt3(log: ((String) -> Unit)? = null)
				= findInHostClasspath("kotlin-annotation-processing(-embeddable).jar",
			Regex("kotlin-annotation-processing(-(embeddable|gradle|maven))?(-[0-9]+\\.[0-9]+\\.[0-9]+)?\\.jar"), log)


        /** Tries to find the tools.jar needed for kapt in the host process' classpath */
        fun findToolsInHostClasspath(log: ((String) -> Unit)? = null)
				= findInHostClasspath("tools.jar", Regex("tools.jar"), log)

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
                =  File(jdkHome.absolutePath + "/../lib/tools.jar").also { check(it.isFile) }

        private fun PrintStream.log(s: String) = println("logging: $s")
        private fun PrintStream.warning(s: String) = println("warning: $s")
        private fun PrintStream.error(s: String) = println("error: $s")
    }
}

internal fun getJavaHome(): File {
    val path = System.getProperty("java.home")
        ?: throw IllegalStateException("no java home found")

    return File(path).also { check(it.isDirectory) }
}

/** Checks if the JDK of the host process is version 9 or later */
internal fun isJdk9OrLater(): Boolean
        = SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0

private fun File.listFilesRecursively(): List<File> {
	return listFiles().flatMap { file ->
		if(file.isDirectory)
			file.listFilesRecursively()
		else
			listOf(file)
	}
}

private fun File.isKotlinFile()
		= listOf("kt", "kts").any{ it.equals(extension, ignoreCase = true) }
