package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

class CompilerPluginsTest{

    @Test
    fun whenCompilerPluginsAreAddedTheyGetExecuted() {

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val result = defaultCompilerConfig().apply {
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin).registerProjectComponents(any(), any())

        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun whenCompilerPluginsAndAnnotationProcessorsAreAddedTheyGetExecuted() {

        val annotationProcessor = object : AbstractProcessor() {
            override fun getSupportedAnnotationTypes(): Set<String> = setOf(ProcessElem::class.java.canonicalName)

            override fun process(p0: MutableSet<out TypeElement>?, p1: RoundEnvironment?): Boolean {
                p1?.getElementsAnnotatedWith(ProcessElem::class.java)?.forEach {
                    Assert.assertEquals("JSource",it?.simpleName.toString())
                }
                return false
            }
        }

        val mockPlugin = Mockito.mock(ComponentRegistrar::class.java)

        val jSource = SourceFile.java(
            "JSource.java", """
				package com.tschuchort.compiletesting;

				@ProcessElem
				class JSource {
					void foo() { }
				}
					"""
        )

        val result = defaultCompilerConfig().apply {
            sources = listOf(jSource)
            annotationProcessors= listOf(annotationProcessor)
            compilerPlugins = listOf(mockPlugin)
            inheritClassPath = true
        }.compile()

        verify(mockPlugin).registerProjectComponents(any(), any())

        Assertions.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}