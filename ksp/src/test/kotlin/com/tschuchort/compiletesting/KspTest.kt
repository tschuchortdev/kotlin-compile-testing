package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class KspTest {
    @Test
    fun failedKspTest() {
        val instance = mock<SymbolProcessor>()
        `when`(instance.process(any())).thenThrow(
            RuntimeException("intentional fail")
        )
        val result = KotlinCompilation().apply {
            sources = listOf(DUMMY_KOTLIN_SRC)
            symbolProcessors = listOf(instance)
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
        assertThat(result.messages).contains("intentional fail")
    }

    @Test
    fun allProcessorMethodsAreCalled() {
        val instance = mock<SymbolProcessor>()
        val result = KotlinCompilation().apply {
            sources = listOf(DUMMY_KOTLIN_SRC)
            symbolProcessors = listOf(instance)
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
        instance.inOrder {
            verify().init(any(), any(), any(), any())
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
        val processor = object : AbstractTestSymbolProcessor() {
            override fun process(resolver: Resolver) {
                val symbols = resolver.getSymbolsWithAnnotation("foo.bar.TestAnnotation")
                assertThat(symbols.size).isEqualTo(1)
                val klass = symbols.first()
                check(klass is KSClassDeclaration)
                val qName = klass.qualifiedName ?: error("should've found qualified name")
                val genPackage = "${qName.getQualifier()}.generated"
                val genClassName = "${qName.getShortName()}_Gen"
                codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = genPackage,
                    fileName = genClassName
                ).bufferedWriter(Charsets.UTF_8).use {
                    it.write("""
                        package $genPackage
                        class $genClassName() {}
                    """.trimIndent())
                }
            }
        }
        val result = KotlinCompilation().apply {
            sources = listOf(annotation, targetClass)
            symbolProcessors = listOf(processor)
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
            import generated.C
            class Dummy(val a:A, val b:B, val c:C)
        """.trimIndent()
        )
        val result = KotlinCompilation().apply {
            sources = listOf(source)
            symbolProcessors = listOf(
                ClassGeneratingProcessor("generated", "A"),
                ClassGeneratingProcessor("generated", "B"))
            symbolProcessors = symbolProcessors + ClassGeneratingProcessor("generated", "C")
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Test
    fun readProcessors() {
        val instance1 = mock<SymbolProcessor>()
        val instance2 = mock<SymbolProcessor>()
        KotlinCompilation().apply {
            symbolProcessors = listOf(instance1)
            assertThat(symbolProcessors).containsExactly(instance1)
            symbolProcessors = listOf(instance2)
            assertThat(symbolProcessors).containsExactly(instance2)
            symbolProcessors = symbolProcessors + instance1
            assertThat(symbolProcessors).containsExactly(instance2, instance1)
        }
    }

    @Test
    fun outputDirectoryContents() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(DUMMY_KOTLIN_SRC)
            symbolProcessors = listOf(ClassGeneratingProcessor("generated", "Gen"))
        }
        val result = compilation.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
        val generatedSources = compilation.kspSourcesDir.walkTopDown().filter {
            it.isFile
        }.toList()
        assertThat(generatedSources).containsExactly(
            compilation.kspSourcesDir.resolve("kotlin/generated/Gen.kt")
        )
    }

    internal open class ClassGeneratingProcessor(
        private val packageName: String,
        private val className: String
    ) : AbstractTestSymbolProcessor() {
        override fun process(resolver: Resolver) {
            super.process(resolver)
            codeGenerator.createNewFile(
                dependencies = Dependencies.ALL_FILES,
                packageName = packageName,
                fileName = className
            ).bufferedWriter(Charsets.UTF_8).use {
                it.write("""
                    package $packageName
                    class $className() {}
                    """.trimIndent())
            }
        }
    }

    @Test
    fun messagesAreReadable() {
        val annotation = SourceFile.kotlin(
            "TestAnnotation.kt", """
            package foo.bar
            annotation class TestAnnotation
        """.trimIndent()
        )
        val targetClass = SourceFile.kotlin(
            "AppCode.kt", """
            package foo.bar
            @TestAnnotation
            class AppCode
        """.trimIndent()
        )
        val processor = object : AbstractTestSymbolProcessor() {
            override fun process(resolver: Resolver) {
                logger.logging("This is a log message")
                logger.info("This is an info message")
                logger.warn("This is an warn message")
                logger.error("This is an error message")
                logger.exception(Throwable("This is a failure"))
            }
        }
        val result = KotlinCompilation().apply {
            sources = listOf(annotation, targetClass)
            symbolProcessors = listOf(processor)
        }.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
        assertThat(result.messages).contains("This is a log message")
        assertThat(result.messages).contains("This is an info message")
        assertThat(result.messages).contains("This is an warn message")
        assertThat(result.messages).contains("This is an error message")
        assertThat(result.messages).contains("This is a failure")
    }

    companion object {
        private val DUMMY_KOTLIN_SRC = SourceFile.kotlin(
            "foo.bar.Dummy.kt", """
            class Dummy {}
        """.trimIndent()
        )
    }
}
