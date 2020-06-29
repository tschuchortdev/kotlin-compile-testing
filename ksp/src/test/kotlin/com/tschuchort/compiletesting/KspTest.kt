package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KspTest {
    @Rule
    @JvmField
    val processorRule = DelegatingSymbolProcessorRule()

    @Test
    fun failedKspTest() {
        val result = KotlinCompilation().apply {
            sources = listOf(DUMMY_KOTLIN_SRC)
            symbolProcessor(processorRule.delegateTo(object : AbstractSymbolProcessor() {
                override fun process(resolver: Resolver) {
                    throw RuntimeException("intentional fail")
                }
            }))
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
        assertThat(result.messages).contains("intentional fail")
    }

    @Test
    fun processorIsCalled() {
        val instance = mock<SymbolProcessor>()
        val result = KotlinCompilation().apply {
            sources = listOf(DUMMY_KOTLIN_SRC)
            symbolProcessor(processorRule.delegateTo(instance))
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
        instance.inOrder {
            verify().init(any(), any(), any())
            verify().process(any())
            verify().finish()
        }
    }

    @Test
    fun processorGeneratedCodeIsVisible() {
        val annotation = SourceFile.kotlin(
            "TestAnnotation.kt", """
            package foo.bar
            annotation class TestAnnotation
        """.trimIndent()
        )
        val targetClass = SourceFile.kotlin(
            "AppCode.kt", """
            package foo.bar
            import foo.bar.generated.AppCode_Gen
            @TestAnnotation
            class AppCode {
                init {
                    // access generated code
                    AppCode_Gen()
                }
            }
        """.trimIndent()
        )
        val processor = object : AbstractSymbolProcessor() {
            override fun process(resolver: Resolver) {
                val symbols = resolver.getSymbolsWithAnnotation("foo.bar.TestAnnotation")
                assertThat(symbols).isNotEmpty()
                val klass = symbols.first()
                check(klass is KSClassDeclaration)
                val qName = klass.qualifiedName ?: error("should've found qualified name")
                val genPackage = "${qName.getQualifier()}.generated"
                val genClassName = "${qName.getShortName()}_Gen"
                val outFile = codeGenerator.createNewFile(
                    packageName = genPackage,
                    fileName = genClassName
                )
                outFile.writeText(
                    """
                        package $genPackage
                        class $genClassName() {}
                    """.trimIndent()
                )
            }
        }
        val result = KotlinCompilation().apply {
            sources = listOf(annotation, targetClass)
            symbolProcessor(processorRule.delegateTo(processor))
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Test
    fun multipleProcessors() {
        // access generated code by multiple processors
        val source = SourceFile.kotlin(
            "foo.bar.Dummy.kt", """
            package foo.bar
            import generated.A
            import generated.B
            class Dummy(val a:A, val b:B)
        """.trimIndent()
        )
        val result = KotlinCompilation().apply {
            sources = listOf(source)
            symbolProcessor(Write_foo_bar_A::class.java, Write_foo_bar_B::class.java)
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Suppress("ClassName")
    internal class Write_foo_bar_A() : ClassGeneratingProcessor("generated", "A")

    @Suppress("ClassName")
    internal class Write_foo_bar_B() : ClassGeneratingProcessor("generated", "B")

    internal open class ClassGeneratingProcessor(
        private val packageName: String,
        private val className: String
    ) : AbstractSymbolProcessor() {
        override fun process(resolver: Resolver) {
            super.process(resolver)
            codeGenerator.createNewFile(packageName, className).writeText(
                """
                package $packageName
                class $className() {}
            """.trimIndent()
            )
        }
    }

    companion object {
        private val DUMMY_KOTLIN_SRC = SourceFile.kotlin(
            "foo.bar.Dummy.kt", """
            class Dummy {}
        """.trimIndent()
        )
    }
}
