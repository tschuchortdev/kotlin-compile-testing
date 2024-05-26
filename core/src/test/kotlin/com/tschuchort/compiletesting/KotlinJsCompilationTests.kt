package com.tschuchort.compiletesting

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.MockitoAdditionalMatchersKotlin.Companion.not
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.mockito.kotlin.*
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.writeText

//@Disabled("These JS tests don't currently work with the new compiler IR")
@Suppress("MemberVisibilityCanBePrivate")
class KotlinJsCompilationTests {

	@Test
	fun `runs with only kotlin sources`() {
		val result = defaultJsCompilerConfig().apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.compiledClassAndResourceFiles).hasSize(1)
		val jsFile = result.compiledClassAndResourceFiles[0]
		assertThat(jsFile.readText()).contains("function KSource()")
	}

	@Test
	fun `runs with no sources`() {
		val result = defaultJsCompilerConfig().apply {
			sources = emptyList()
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `runs with SourceFile from path`(@TempDir tempDir: Path) {
		val sourceFile = tempDir.resolve("KSource.kt").createFile().apply {
			writeText("class KSource")
		}

		val result = defaultJsCompilerConfig().apply {
			sources = listOf(SourceFile.fromPath(sourceFile.toFile()))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.compiledClassAndResourceFiles).hasSize(1)
		val jsFile = result.compiledClassAndResourceFiles[0]
		assertThat(jsFile.readText()).contains("function KSource()")
	}

	@Test
	fun `runs with SourceFile from paths with filename conflicts`(@TempDir tempDir: Path) {
		tempDir.resolve("a").createDirectory()
		val sourceFileA = tempDir.resolve("a/KSource.kt").createFile().apply {
			writeText("package a\n\nclass KSource")
		}

		tempDir.resolve("b").createDirectory()
		val sourceFileB = tempDir.resolve("b/KSource.kt").createFile().apply {
			writeText("package b\n\nclass KSource")
		}

		val result = defaultJsCompilerConfig().apply {
			sources = listOf(
				SourceFile.fromPath(sourceFileA.toFile()),
				SourceFile.fromPath(sourceFileB.toFile()))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.compiledClassAndResourceFiles).hasSize(1)
		val jsFile = result.compiledClassAndResourceFiles[0]
		assertThat(jsFile.readText()).contains("function KSource() {")
		assertThat(jsFile.readText()).contains("function KSource_0() {")
	}

	@Test
	fun `detects the plugin provided for compilation via pluginClasspaths property`() {
		val result = defaultJsCompilerConfig().apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			pluginClasspaths = listOf(classpathOf("kotlin-scripting-compiler-${BuildConfig.KOTLIN_VERSION}"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(
			"provided plugin org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar"
		)
	}

	@Test
	fun `returns an internal error when adding a non existing plugin for compilation`() {
		val result = defaultJsCompilerConfig().apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			pluginClasspaths = listOf(File("./non-existing-plugin.jar"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
		assertThat(result.messages).contains("non-existing-plugin.jar not found")
	}

	@Test
	fun `Custom plugin receives CLI argument`() {
	    val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
				class KSource
			""".trimIndent()
		)

		val cliProcessor = spy(object : CommandLineProcessor {
			override val pluginId = "myPluginId"
			override val pluginOptions = listOf(CliOption("test_option_name", "", ""))
		})

		val result = defaultJsCompilerConfig().apply {
			sources = listOf(kSource)
			inheritClassPath = false
			pluginOptions = listOf(PluginOption("myPluginId", "test_option_name", "test_value"))
			commandLineProcessors = listOf(cliProcessor)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		verify(cliProcessor, atLeastOnce()).processOption(argWhere<AbstractCliOption> { it.optionName == "test_option_name" }, eq("test_value"), any())
		verify(cliProcessor, never()).processOption(argWhere<AbstractCliOption> { it.optionName == "test_option_name" }, not(eq("test_value")), any())
		verify(cliProcessor, never()).processOption(argWhere<AbstractCliOption> { it.optionName != "test_option_name" }, any(), any())
	}
}
