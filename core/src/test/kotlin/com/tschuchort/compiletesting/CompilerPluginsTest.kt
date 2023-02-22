package com.tschuchort.compiletesting

import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify

class CompilerPluginsTest {

    @Test
    fun `when compiler plugins are added they get executed`() {

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val result = defaultCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
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
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())

        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `when JS compiler plugins are added they get executed`() {
        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val result = defaultJsCompilerConfig().apply {
            sources = listOf(SourceFile.new("emptyKotlinFile.kt", ""))
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin, atLeastOnce()).registerProjectComponents(any(), any())
        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
