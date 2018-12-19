package com.tschuchort.kotlinelements

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.annotation.processing.Processor

@InspectionRoot
class SmokeTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	@Test
	fun `compiles successfully`() {
		val source = KotlinCompilation.SourceFile("source.kt",
				"""package com.tschuchort.kotlinelements

                    @InspectionRoot
					fun main(args: Array<String>) {
						println("hello")
					}
				""".trimIndent()
		)

		val result = KotlinCompilation(
				workingDir = temporaryFolder.root,
				sources = listOf(source),
				services = listOf(KotlinCompilation.Service(Processor::class, TestProcessor::class)),
				jdkHome = findJavaHome().parentFile,
				inheritClassPath = true,
				skipRuntimeVersionCheck = true,
				verbose = true
		).run()

        print(result.messages)
		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

	}
}