package com.tschuchort.compiletest

import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.metadata.js.JsProtoBuf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintStream
import javax.annotation.processing.Processor
import java.util.logging.Filter
import java.util.logging.LogRecord

class SmokeTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	@Test
	fun `compilation succeeds`() {


		val source = KotlinCompilation.SourceFile("source.kt",
				"""//import com.tschuchort.compiletest.InspectionRoot
					import javax.lang.model.SourceVersion
					import java.io.File

                    //@InspectionRoot
					fun main(args: Array<String>) {
						File("")
						println("hello")
					}
				""".trimIndent()
		)

		val systemOutBuffer = Buffer()

		val result = KotlinCompilation(
			workingDir = temporaryFolder.root,
			sources = listOf(source),
			services = listOf(KotlinCompilation.Service(Processor::class, TestProcessor::class)),
			//jdkHome = File("D:\\Program Files\\Java\\jdk1.8.0_25"),//getJavaHome(),
			//kotlinHome = File("C:\\kotlin-compiler"),
			//toolsJar = File("D:\\Program Files\\Java\\jdk1.8.0_25\\lib\\tools.jar"),
			inheritClassPath = true,
			skipRuntimeVersionCheck = true,
			correctErrorTypes = true,
			verbose = true,
			reportOutputFiles = true,
			systemOut = PrintStream(systemOutBuffer.outputStream())
		).run()

		print(systemOutBuffer.readUtf8())

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}


}