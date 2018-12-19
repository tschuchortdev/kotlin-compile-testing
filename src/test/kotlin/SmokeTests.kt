package com.tschuchort.kotlinelements

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
				dir = temporaryFolder.root,
				sources = listOf(source),
				inheritClassPath = true
		).apply {
            addService(Processor::class, TestProcessor())
        }.run()

        print(result.messages)
		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

	}
}