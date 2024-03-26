package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import java.net.URL
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class CompilerPluginsTest {

    @Test
    fun `when compiler plugins are added they get executed`() {

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)
        val fakeRegistrar = FakeCompilerPluginRegistrar()

        val result = defaultCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            componentRegistrars = listOf(mockPlugin)
            compilerPluginRegistrars = listOf(fakeRegistrar)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())
        fakeRegistrar.assertRegistered()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `when compiler plugins and annotation processors are added they get executed`() {

        val annotationProcessor = object : AbstractProcessor() {
            override fun getSupportedAnnotationTypes(): Set<String> = setOf(ProcessElem::class.java.canonicalName)

            override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {
                p1?.getElementsAnnotatedWith(ProcessElem::class.java)?.forEach {
                    Assert.assertEquals("JSource", it?.simpleName.toString())
                }
                return false
            }
        }

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

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
            componentRegistrars = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `convert jar url resource to path without decoding encoded path`() {
        // path on disk has "url%3Aport" path segment, but it's encoded from classLoader.getResources()
        val absolutePath = "jar:file:/path/to/jar/url%253Aport/core-0.4.0.jar!" +
                "/META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
        val resultPath = KotlinCompilation().urlToResourcePath(URL(absolutePath)).toString()
        assertThat(resultPath.contains("url:")).isFalse()
        assertThat(resultPath.contains("url%25")).isFalse()
        assertThat(resultPath.contains("url%3A")).isTrue()
    }

    @Test
    fun `convert file url resource to path without decoding`() {
        // path on disk has "repos%3Aoss" path segment, but it's encoded from classLoader.getResources()
        val absolutePath = "file:/Users/user/repos%253Aoss/kotlin-compile-testing/core/build/resources/main"
        val resultPath = KotlinCompilation().urlToResourcePath(URL(absolutePath)).toString()
        assertThat(resultPath.contains("repos:")).isFalse()
        assertThat(resultPath.contains("repos%25")).isFalse()
        assertThat(resultPath.contains("repos%3A")).isTrue()
    }
}

