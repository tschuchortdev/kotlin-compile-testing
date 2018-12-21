package com.tschuchort.compiletest

import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintStream
import javax.annotation.processing.Processor

class SmokeTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	@Test
	fun `compilation succeeds`() {
		val kSource = KotlinCompilation.SourceFile("kSource.kt",
				"""import com.tschuchort.compiletest.Marker
					import javax.lang.model.SourceVersion
					import java.io.File
					import com.tschuchort.compiletest.GeneratedClass

					class KotClass {
						fun brr() {}
					}

					@Marker
					fun foo() {
						JSource().bar()
					}

					fun main(freeArgs: Array<String>) {
						File("")
						println("hello")
						GeneratedClass().foo()
					}
				""".trimIndent()
		)

		val jSource = KotlinCompilation.SourceFile("JSource.java",
				"""
    			import com.tschuchort.compiletest.Marker;
    			public class JSource {
    				@Marker public void bar() {
    					(new KotClass()).brr();
    				}
                }
				""".trimIndent())

		val systemOutBuffer = Buffer()

		val result = KotlinCompilation(
			workingDir = File("C:\\compile-testing"),
			sources = listOf(kSource, jSource),
			services = listOf(KotlinCompilation.Service(Processor::class, TestProcessor::class)),
			jdkHome = File("D:\\Program Files\\Java\\jdk1.8.0_25"),//getJavaHome(),
			//toolsJar = File("D:\\Program Files\\Java\\jdk1.8.0_25\\lib\\tools.jar"),
			inheritClassPath = true,
			skipRuntimeVersionCheck = true,
			correctErrorTypes = true,
			verbose = true,
			//reportOutputFiles = true,
			systemOut = PrintStream(systemOutBuffer.outputStream())
		).run()

		print(systemOutBuffer.readUtf8())

		print(result.generatedFiles)

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}


}