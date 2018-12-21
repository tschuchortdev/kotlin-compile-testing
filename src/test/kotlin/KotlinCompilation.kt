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
import org.jetbrains.kotlin.cli.common.arguments.InternalArgument
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.*
import java.net.URLClassLoader
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.lang.model.SourceVersion

@Suppress("unused", "MemberVisibilityCanBePrivate")
class KotlinCompilation(
    /** Working directory for the compilation */
	val workingDir: File,
    /** Arbitrary arguments to be passed to kotlinc */
	val args: List<String> = emptyList(),
    /** Arbitrary arguments to be passed to kapt */
	val kaptArgs: Map<String, String> = emptyMap(),
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
    /** Search path for Kotlin runtime libraries */
	val kotlinHome: File? = null,
    /**
	 * Don't look for kotlin-stdlib.jar, kotlin-script-runtime.jar
	 * and kotlin-reflect.jar in the [kotlinHome] directory. They
	 * need to be provided by [classpaths] or will be unavailable.
	 * */
	val noStdLib: Boolean = kotlinHome == null,
    /**
	 * Don't look for kotlin-reflect.jar in the [kotlinHome] directory.
	 * It has to be provided by [classpaths] or will be unavailable.
	 *
	 * Setting it to false has no effect when [noStdLib] is true.
	 */
	val noReflect: Boolean = noStdLib,
    /**
	 * Path to the tools.jar file needed for kapt when using a JDK 8.
	 *
	 * Note: Using a tools.jar file with a JDK 9 or later leads to an
	 * internal compiler error!
	 */
	val toolsJar: File? = findToolsJarInHostClasspath(
        log = { if(verbose) systemOut.log(it) }
    ),
    /**
     * Path to the kotlin-annotation-processing-embeddable*.jar that
     * contains kapt3.
     *
     * Only needed when [services] is not empty.
     */
    val kapt3Jar: File? = findKapt3JarInHostClasspath(
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
	val sourcesDir = File(workingDir, "sources")
	val classesDir = File(workingDir, "classes")

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
	data class Result(val exitCode: ExitCode)

	/** A service that will be passed to kapt */
	data class Service<S : Any, T : S>(val serviceClass: KClass<S>, val implementationClass: KClass<T>)

	val allClasspaths: List<String> = mutableListOf<String>().apply {
		addAll(classpaths.map(File::getAbsolutePath))

		if(inheritClassPath) {
			val hostClasspaths = getClasspaths().map(File::getAbsolutePath)
			addAll(hostClasspaths)

			if(verbose)
				systemOut.log("Inheriting classpaths:  " + hostClasspaths.joinToString(File.pathSeparator))
		}
	}

    /** Returns arguments necessary to enable and configure kapt3. */
    private fun annotationProcessorArgs() = object {
        private val kaptSourceDir = File(workingDir, "kapt/sources")
        private val kaptStubsDir = File(workingDir, "kapt/stubs")

        init {
            require(kapt3Jar != null) { "kapt3Jar has to be non-null if annotation processing is used" }
        }

        val pluginClassPaths = listOfNotNull(kapt3Jar!!.absolutePath, toolsJar?.absolutePath).toTypedArray()

        val pluginOptions = arrayOf(
            "plugin:org.jetbrains.kotlin.kapt3:sources=${kaptSourceDir.absolutePath}",
            "plugin:org.jetbrains.kotlin.kapt3:classes=${classesDir.absolutePath}",
            "plugin:org.jetbrains.kotlin.kapt3:stubs=${kaptStubsDir.absolutePath}",
            "plugin:org.jetbrains.kotlin.kapt3:apclasspath=${servicesJar.absolutePath}",
            "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=$correctErrorTypes",
            // Don't forget aptMode! Without it, the compiler will crash with an obscure error about
            // write unsafe context
            "plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt",
			*if (kaptArgs.isNotEmpty())
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:apoptions=${encodeOptionsForKapt(kaptArgs)}")
			else
				emptyArray()
        )
    }

	/** Runs the compilation task */
	fun run(): Result {
        // write given sources to working directory
		sources.forEach { it.writeTo(sourcesDir) }

        // setup arguments for the compiler call
        val k2jvmArgs = K2JVMCompilerArguments().also { k2JvmArgs ->
            k2JvmArgs.freeArgs = sourcesDir.listFiles().map { it.absolutePath } + args

			// only add kapt stuff if there are services that may use it
			if(services.isNotEmpty()) {
                val annotationProcArgs = annotationProcessorArgs()

                k2JvmArgs.pluginOptions = if(k2JvmArgs.pluginOptions != null)
                    k2JvmArgs.pluginOptions!!.plus(annotationProcArgs.pluginOptions)
				else
					annotationProcArgs.pluginOptions

                k2JvmArgs.pluginClasspaths = if(k2JvmArgs.pluginClasspaths != null)
                    k2JvmArgs.pluginClasspaths!!.plus(annotationProcArgs.pluginClassPaths)
				else
                    annotationProcArgs.pluginClassPaths
			}
			else if(verbose) {
				systemOut.log("No services were given. Not including kapt in the compiler's plugins.")
			}

            k2JvmArgs.destination = classesDir.absolutePath
            k2JvmArgs.classpath = allClasspaths.joinToString(separator = File.pathSeparator)

			if(jdkHome != null) {
                k2JvmArgs.jdkHome = jdkHome.absolutePath
			}
			else {
                if(verbose)
                    systemOut.log("Using option -no-jdk. Kotlinc won't look for a JDK.")

                k2JvmArgs.noJdk = true
			}

            k2JvmArgs.noStdlib = noStdLib
            k2JvmArgs.noReflect = noReflect

			kotlinHome?.let { k2JvmArgs.kotlinHome = it.absolutePath }

			jvmTarget?.let { k2JvmArgs.jvmTarget = it }

            k2JvmArgs.verbose = verbose
            k2JvmArgs.skipRuntimeVersionCheck = skipRuntimeVersionCheck
            k2JvmArgs.suppressWarnings = suppressWarnings
            k2JvmArgs.allWarningsAsErrors = allWarningsAsErrors
            k2JvmArgs.reportOutputFiles = reportOutputFiles
            k2JvmArgs.reportPerf = reportPerformance
            k2JvmArgs.reportOutputFiles = reportOutputFiles
            k2JvmArgs.loadBuiltInsFromDependencies = loadBuiltInsFromDependencies


			k2JvmArgs.additionalJavaModules=arrayOf()
			k2JvmArgs.javaModulePath=""
        }

        val compilerSystemOutBuffer = Buffer()  // Buffer for capturing compiler's logging output

        val exitCode = K2JVMCompiler().execImpl(
            PrintingMessageCollector(
				PrintStream(
                    TeeOutputStream(systemOut, compilerSystemOutBuffer.outputStream())),
                MessageRenderer.WITHOUT_PATHS, true),
            Services.EMPTY,
            k2jvmArgs
        )

        // check for known errors that are hard to debug for the user
		if (exitCode == ExitCode.INTERNAL_ERROR && compilerSystemOutBuffer.readUtf8()
				.contains("No enum constant com.sun.tools.javac.main.Option.BOOT_CLASS_PATH")) {

			systemOut.warning(
				"${this::class.simpleName} has detected that the compilation failed with an error that may be " +
						"caused by including a tools.jar file together with a JDK of version 9 or later. " +
						if (inheritClassPath)
							"Make sure that no tools.jar (or unwanted JDK) is in the inherited classpath"
						else ""
			)
		}

        return Result(exitCode)
	}


	/**
	 * Base64 encodes a mapping of annotation processor args for kapt, as specified by
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
        /** Tries to find the kapt 3 jar in the host classpath */
        fun findKapt3JarInHostClasspath(log: ((String) -> Unit)? = null): File? {
            val jarFile = getClasspaths().firstOrNull { classpath ->
                classpath.name.matches(Regex("kotlin-annotation-processing-embeddable.*?\\.jar"))
                //TODO("check that jar file actually contains the right classes")
            }

            if(jarFile == null && log != null)
                log("Searched classpath for kotlin-annotation-processing-embeddable*.jar but didn't find anything.")
            else if(log != null)
                log("Searched classpath for kapt3 jar and found: $jarFile")

            return jarFile
        }

        /** Tries to find the tools.jar needed for kapt in the host classpath */
        fun findToolsJarInHostClasspath(log: ((String) -> Unit)? = null): File? {
            val jarFile = getClasspaths().firstOrNull { classpath ->
                classpath.name.matches(Regex("tools.jar"))
                //TODO("check that jar file actually contains the right classes")
            }

            if(jarFile == null && log != null)
                log("Searched classpath for tools.jar but didn't find anything.")
            else if(log != null)
                log("Searched classpath for tools.jar and found: $jarFile")

            return jarFile
        }

        /** Returns the files on the classloader's classpath and modulepath */
        fun getClasspaths(): List<File> {
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
        private fun PrintStream.error(s: String) = println("warning: $s")
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