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
package com.tschuchort.kotlinelements

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
import java.io.ObjectOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.net.URLDecoder

private fun findJavaHome(): File {
	val path = System.getProperty("java.home")
				?: throw IllegalStateException("no java home found")

	return File(path).also { check(it.isDirectory) }
}

private fun findToolsJar(javaHome: File): File
		=  File(javaHome.absolutePath + "/../lib/tools.jar").also { check(it.isFile) }


class KotlinCompilation(
	val dir: File,
	val args: List<String> = emptyList(),
	val kaptArgs: Map<String, String> = emptyMap(),
	classpaths: List<String> = emptyList(),
	val sources: List<SourceFile> = emptyList(),
	private val jdkHome: File = findJavaHome(),
	toolsJar: File = findToolsJar(jdkHome),
	inheritClassPath: Boolean = false,
	correctErrorTypes: Boolean = false,
	private val skipRuntimeVersionCheck: Boolean = false
) {
	val sourcesDir = File(dir, "sources")
	val classesDir = File(dir, "classes")

    private val services = Services.Builder()

    fun <T : Any> addService(serviceClass: KClass<T>, implementation: T) {
        services.register(serviceClass.java, implementation)
    }

	//private val servicesGroupedByClass = services.groupBy({ it.serviceClass }, { it.implementation })

	/**
	 * A Kotlin source file to be compiled
	 */
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

	data class Result(val messages: String, val exitCode: ExitCode)

	val allClasspaths: List<String> = mutableListOf<String>().apply {
		addAll(classpaths)

		if(inheritClassPath)
			addAll(getHostProcessClasspathFiles().map(File::getAbsolutePath))
	}

    /** Returns arguments necessary to enable and configure kapt3. */
    private val annotationProcessorArgs = object {
        private val kaptSourceDir = File(dir, "kapt/sources")
        private val kaptStubsDir = File(dir, "kapt/stubs")

        val pluginClassPaths = arrayOf(getKapt3Jar().absolutePath, toolsJar.absolutePath)
        val pluginOptions = arrayOf(
            "plugin:org.jetbrains.kotlin.kapt3:sources=$kaptSourceDir",
            "plugin:org.jetbrains.kotlin.kapt3:classes=$classesDir",
            "plugin:org.jetbrains.kotlin.kapt3:stubs=$kaptStubsDir",
            "plugin:org.jetbrains.kotlin.kapt3:apclasspath=$sourcesDir",
            "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=$correctErrorTypes",
            // Don't forget aptMode! Without it, the compiler will crash with an obscure error about
            // write unsafe context
            "plugin:org.jetbrains.kotlin.kapt3:aptMode=stubsAndApt",
			"plugin:org.jetbrains.kotlin.kapt3:processors=com.tschuchort.kotlinelements.TestProcessor",
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
            freeArgs = sourcesDir.listFiles().map { it.absolutePath }
            pluginOptions = annotationProcessorArgs.pluginOptions
            pluginClasspaths = annotationProcessorArgs.pluginClassPaths
            loadBuiltInsFromDependencies = true
            destination = classesDir.absolutePath
            classpath = allClasspaths.joinToString(separator = File.pathSeparator)
            noStdlib = true
            noReflect = true
            skipRuntimeVersionCheck = this@KotlinCompilation.skipRuntimeVersionCheck
            reportPerf = false
			reportOutputFiles = true
			jdkHome = this@KotlinCompilation.jdkHome.absolutePath
        }

        val compilerOutputbuffer = Buffer()

        val exitCode = K2JVMCompiler().execImpl(
            PrintingMessageCollector(PrintStream(compilerOutputbuffer.outputStream()),
                MessageRenderer.WITHOUT_PATHS, true),
            Services.EMPTY,//services.build(),
            k2jvmArgs
        )

        return Result(compilerOutputbuffer.readUtf8(), exitCode)
	}


	/** Returns the files on the host process' classpath. */
	private fun getHostProcessClasspathFiles(): List<File> {
		val classLoader: ClassLoader = SmokeTests::class.java.classLoader
		if (classLoader !is URLClassLoader) {
			throw UnsupportedOperationException("unable to extract classpath from $classLoader")
		}

		return classLoader.urLs.map { url ->
			if (url.protocol != "file") {
				throw UnsupportedOperationException("unable to handle classpath element $url")
			}

			File(URLDecoder.decode(url.path, "UTF-8"))
		}
	}

	/** Returns the path to the kotlin-annotation-processing .jar file. */
	private fun getKapt3Jar(): File {
		return getHostProcessClasspathFiles().firstOrNull { file ->
			file.name.startsWith("kotlin-annotation-processing-embeddable")
		}
			   ?: throw IllegalStateException(
				"no kotlin-annotation-processing-embeddable jar on classpath:\n  " +
				"${getHostProcessClasspathFiles().joinToString(separator = "\n  ")}}")
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
