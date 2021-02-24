package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
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
import java.util.concurrent.atomic.AtomicInteger

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
            override fun process(resolver: Resolver): List<KSAnnotated> {
                val symbols = resolver.getSymbolsWithAnnotation("foo.bar.TestAnnotation")
                if (symbols.isNotEmpty())  {
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
                return emptyList()
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
    fun incremental() {
        KotlinCompilation().apply {
            // Disabled by default
            assertThat(kspIncremental).isFalse()
            assertThat(kspIncrementalLog).isFalse()
            kspIncremental = true
            assertThat(kspIncremental).isTrue()
            kspIncrementalLog = true
            assertThat(kspIncrementalLog).isTrue()
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

    @Test
    fun findSymbols() {
        val javaSource = SourceFile.java(
            "JavaSubject.java",
            """
            @${SuppressWarnings::class.qualifiedName}("")
            class JavaSubject {}
            """.trimIndent()
        )
        val kotlinSource = SourceFile.kotlin(
            "KotlinSubject.kt",
            """
            @${SuppressWarnings::class.qualifiedName}("")
            class KotlinSubject {}
            """.trimIndent()
        )
        val result = mutableListOf<String>()
        val processor = object : AbstractTestSymbolProcessor() {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                resolver.getSymbolsWithAnnotation(
                    SuppressWarnings::class.java.canonicalName
                ).filterIsInstance<KSClassDeclaration>()
                    .forEach {
                        result.add(it.qualifiedName!!.asString())
                    }
                return emptyList()
            }
        }
        val compilation = KotlinCompilation().apply {
            sources = listOf(javaSource, kotlinSource)
            symbolProcessors += processor
        }
        compilation.compile()
        assertThat(result).containsExactlyInAnyOrder(
            "JavaSubject", "KotlinSubject"
        )
    }

    internal open class ClassGeneratingProcessor(
        private val packageName: String,
        private val className: String,
        times: Int = 1
    ) : AbstractTestSymbolProcessor() {
        val times = AtomicInteger(times)
        override fun process(resolver: Resolver): List<KSAnnotated> {
            super.process(resolver)
            if (times.decrementAndGet() == 0) {
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
            return emptyList()
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
            override fun process(resolver: Resolver): List<KSAnnotated> {
                logger.logging("This is a log message")
                logger.info("This is an info message")
                logger.warn("This is an warn message")
                logger.error("This is an error message")
                logger.exception(Throwable("This is a failure"))
                return emptyList()
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
