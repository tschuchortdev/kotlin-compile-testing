package com.tschuchort.compiletesting

import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.net.URL
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify

@Suppress("DEPRECATION")
class CompilerPluginsTest {

    @Test
    fun `when ComponentRegistrar plugins are added they get executed`() {

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val result = defaultCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            componentRegistrars = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `when CompilerPluginRegistrar plugins are added they get executed`() {
        val fakePlugin = FakeCompilerPluginRegistrar()

        val result = defaultCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            compilerPluginRegistrars = listOf(fakePlugin)
            inheritClassPath = true
        }.compile()

        fakePlugin.assertRegistered()
        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `when compiler plugins and annotation processors are added they get executed`() {

        val annotationProcessor = object : AbstractProcessor() {
            override fun getSupportedAnnotationTypes(): Set<String> = setOf(ProcessElem::class.java.canonicalName)

            override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {
                p1?.getElementsAnnotatedWith(ProcessElem::class.java)?.forEach {
                    Assertions.assertThat("JSource").isEqualTo(it?.simpleName.toString())
                }
                return false
            }
        }

        val mockLegacyPlugin = Mockito.mock(ComponentRegistrar::class.java)
        val fakePlugin = FakeCompilerPluginRegistrar()

        val jSource = SourceFile.kotlin(
            "JSource.kt", """
				package com.tschuchort.compiletesting;

				@ProcessElem
				class JSource {
					fun foo() { }
				}
					"""
        )

        val result = defaultCompilerConfig().apply {
            sources = listOf(jSource)
            annotationProcessors = listOf(annotationProcessor)
            componentRegistrars = listOf(mockLegacyPlugin)
            compilerPluginRegistrars = listOf(fakePlugin)
            inheritClassPath = true
        }.compile()

        verify(mockLegacyPlugin, atLeastOnce()).registerProjectComponents(any(), any())
        fakePlugin.assertRegistered()
        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    //@Disabled("JS tests don't currently work with the new compiler IR")
    @Test
    fun `when JS compiler plugins are added they get executed`() {
        val mockLegacyPlugin = Mockito.mock(ComponentRegistrar::class.java)
        val fakePlugin = FakeCompilerPluginRegistrar()

        val result = defaultJsCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            componentRegistrars = listOf(mockLegacyPlugin)
            compilerPluginRegistrars = listOf(fakePlugin)
            inheritClassPath = true
            disableStandardScript = true
        }.compile()

        verify(mockLegacyPlugin, atLeastOnce()).registerProjectComponents(any(), any())
        fakePlugin.assertRegistered()
        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `convert jar url resource to path without decoding encoded path`() {
        // path on disk has "url%3Aport" path segment, but it's encoded from classLoader.getResources()
        val absolutePath = "jar:file:/path/to/jar/url%253Aport/core-0.4.0.jar!" +
                "/META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
        val resultPath = KotlinCompilation().urlToResourcePath(URL(absolutePath)).toString()
        Assertions.assertThat(resultPath.contains("url:")).isFalse()
        Assertions.assertThat(resultPath.contains("url%25")).isFalse()
        Assertions.assertThat(resultPath.contains("url%3A")).isTrue()
    }

    @Test
    fun `convert file url resource to path without decoding`() {
        // path on disk has "repos%3Aoss" path segment, but it's encoded from classLoader.getResources()
        val absolutePath = "file:/Users/user/repos%253Aoss/kotlin-compile-testing/core/build/resources/main"
        val resultPath = KotlinCompilation().urlToResourcePath(URL(absolutePath)).toString()
        Assertions.assertThat(resultPath.contains("repos:")).isFalse()
        Assertions.assertThat(resultPath.contains("repos%25")).isFalse()
        Assertions.assertThat(resultPath.contains("repos%3A")).isTrue()
    }
}
