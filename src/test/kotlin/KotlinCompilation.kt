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

import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
import kotlin.reflect.KClass
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun findJavaHome(): File {
	val path = System.getProperty("java.home")
				?: throw IllegalStateException("no java home found")

	return File(path).also { check(it.isDirectory) }
}

internal fun findToolsJar(jdkHome: File): File
		=  File(jdkHome.absolutePath + "/../lib/tools.jar").also { check(it.isFile) }

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
	val jdkHome: File? = findJavaHome(),
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
	/** Path to the tools.jar file needed for kapt */
	toolsJar: File = findToolsJar(findJavaHome()),
	/** Inherit classpath from calling process */
	inheritClassPath: Boolean = false,
	val jvmTarget: String? = null,
	correctErrorTypes: Boolean = true,
	val skipRuntimeVersionCheck: Boolean = false,
	val verbose: Boolean = false,
	val suppressWarnings: Boolean = false,
	val allWarningsAsErrors: Boolean = false,
	val reportOutputFiles: Boolean = false
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
	data class Result(val messages: String, val exitCode: ExitCode)

	/** A service that will be passed to kapt */
	data class Service<S : Any, T : S>(val serviceClass: KClass<S>, val implementationClass: KClass<T>)

	val allClasspaths: List<String> = mutableListOf<String>().apply {
		addAll(classpaths.map(File::getAbsolutePath))

		add(servicesJar.absolutePath)

		if(inheritClassPath) {
			val hostClasspaths = getHostProcessClasspaths().map(File::getAbsolutePath)
			addAll(hostClasspaths)
		}

	}

    /** Returns arguments necessary to enable and configure kapt3. */
    private val annotationProcessorArgs = object {
        private val kaptSourceDir = File(workingDir, "kapt/sources")
        private val kaptStubsDir = File(workingDir, "kapt/stubs")

        val pluginClassPaths = arrayOf(getKapt3Jar().absolutePath, toolsJar.absolutePath)
        val pluginOptions = arrayOf(
            "plugin:org.jetbrains.kotlin.kapt3:sources=$kaptSourceDir",
            "plugin:org.jetbrains.kotlin.kapt3:classes=$classesDir",
            "plugin:org.jetbrains.kotlin.kapt3:stubs=$kaptStubsDir",
            "plugin:org.jetbrains.kotlin.kapt3:apclasspath=$servicesJar",
            "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=$correctErrorTypes",
            // Don't forget aptMode! Without it, the compiler will crash with an obscure error about
            // write unsafe context
            "plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt",
			*if (kaptArgs.isNotEmpty())
				arrayOf("plugin:org.jetbrains.kotlin.kapt3:apoptions=${encodeOptions(kaptArgs)}")
			else
				emptyArray()
        )
    }

	fun run(): Result {
		sources.forEach {
            it.writeTo(sourcesDir)
        }

        val k2jvmArgs = K2JVMCompilerArguments().apply {
            freeArgs = sourcesDir.listFiles().map { it.absolutePath } + args
            pluginOptions = annotationProcessorArgs.pluginOptions
            pluginClasspaths = annotationProcessorArgs.pluginClassPaths
            loadBuiltInsFromDependencies = true
            destination = classesDir.absolutePath
            classpath = allClasspaths.joinToString(separator = File.pathSeparator)
            //noStdlib = true
            //noReflect = true
            skipRuntimeVersionCheck = this@KotlinCompilation.skipRuntimeVersionCheck
            reportPerf = false
			reportOutputFiles = true

			if(this@KotlinCompilation.jdkHome != null) {
				jdkHome = this@KotlinCompilation.jdkHome.absolutePath
			}
			else {
				noJdk = true
			}

			noStdlib = this@KotlinCompilation.noStdLib
			noReflect = this@KotlinCompilation.noReflect

			this@KotlinCompilation.kotlinHome?.let {
				kotlinHome = it.absolutePath
			}

			this@KotlinCompilation.jvmTarget?.let {
				jvmTarget = it
			}

			verbose = this@KotlinCompilation.verbose
			suppressWarnings = this@KotlinCompilation.suppressWarnings
			allWarningsAsErrors = this@KotlinCompilation.allWarningsAsErrors
			reportOutputFiles = this@KotlinCompilation.reportOutputFiles
        }

        val compilerOutputbuffer = Buffer()

        val exitCode = K2JVMCompiler().execImpl(
            PrintingMessageCollector(PrintStream(compilerOutputbuffer.outputStream()),
                MessageRenderer.WITHOUT_PATHS, true),
            Services.EMPTY,
            k2jvmArgs
        )

        return Result(compilerOutputbuffer.readUtf8(), exitCode)
	}


	/** Returns the files on the host process' classpath. */
	private fun getHostProcessClasspaths(): List<File> {
		val classLoader: ClassLoader = this::class.java.classLoader
		if (classLoader is URLClassLoader) {
            return classLoader.urLs.map { url ->
                if (url.protocol != "file") {
                    throw UnsupportedOperationException("unable to handle classpath element $url")
                }

                File(URLDecoder.decode(url.path, "UTF-8"))
            }
		}
        else {
            val jarsOrZips = classLoader.getResources("META-INF").toList().mapNotNull {
				if(it.path.matches(Regex("file:/.*?(\\.jar|\\.zip)!/META-INF")))
					it.path.removeSurrounding("file:/", "!/META-INF")
				else
					null
			}

            val dirs = classLoader.getResources("").toList().map { it.path }
            val wildcards = classLoader.getResources("*").toList().map { it.path }

			val result = (jarsOrZips + dirs + wildcards).map {
				// paths may contain percent-encoded characters like %20 for space
				File(URLDecoder.decode(it, "UTF-8"))
			}

			return result

            /*val x = this::class.java.classLoader.run {
                unnamedModule.packages.map {
                    val res = getResource(it)
                    res
                }
            }*/
        }
	}

	/** Returns the path to the kotlin-annotation-processing .jar file. */
	private fun getKapt3Jar(): File {
		return getHostProcessClasspaths().firstOrNull { file ->
			file.name.startsWith("kotlin-annotation-processing-embeddable")
		}
			   ?: throw IllegalStateException(
				"no kotlin-annotation-processing-embeddable jar on classpath:\n  " +
				"${getHostProcessClasspaths().joinToString(separator = "\n  ")}}")
	}

	/**
	 * Base64 encodes a mapping of annotation processor args for kapt, as specified by
	 * https://kotlinlang.org/docs/reference/kapt.html#apjavac-options-encoding
	 */
	private fun encodeOptions(options: Map<String, String>): String {
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
}
