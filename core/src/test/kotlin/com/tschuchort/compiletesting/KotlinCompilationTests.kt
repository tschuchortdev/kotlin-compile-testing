package com.tschuchort.compiletesting

import com.nhaarman.mockitokotlin2.*
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.MockitoAdditionalMatchersKotlin.Companion.not
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.PluginCliOptionProcessingException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

@Suppress("MemberVisibilityCanBePrivate")
class KotlinCompilationTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	val kotlinTestProc = KotlinTestProcessor()
	val javaTestProc = JavaTestProcessor()

	@Test
	fun `runs with only kotlin sources`() {
		val result = defaultCompilerConfig().apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSource")
	}

	@Test
	fun `runs with only java sources`() {
		val result = defaultCompilerConfig().apply {
			sources = listOf(SourceFile.java("JSource.java", "class JSource {}"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "JSource")
	}

	@Test
	fun `runs with no sources`() {
		val result = defaultCompilerConfig().apply {
			sources = emptyList()
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `runs with SourceFile from path`() {
		val sourceFile = temporaryFolder.newFile("KSource.kt").apply {
			writeText("class KSource")
		}

		val result = defaultCompilerConfig().apply {
			sources = listOf(SourceFile.fromPath(sourceFile))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSource")
	}

	@Test
	fun `runs with SourceFile from paths with filename conflicts`() {
		temporaryFolder.newFolder("a")
		val sourceFileA = temporaryFolder.newFile("a/KSource.kt").apply {
			writeText("package a\n\nclass KSource")
		}

		temporaryFolder.newFolder("b")
		val sourceFileB = temporaryFolder.newFile("b/KSource.kt").apply {
			writeText("package b\n\nclass KSource")
		}

		val result = defaultCompilerConfig().apply {
			sources = listOf(
				SourceFile.fromPath(sourceFileA),
				SourceFile.fromPath(sourceFileB))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "a.KSource")
		assertClassLoadable(result, "b.KSource")
	}

	@Test
	fun `Kotlin can access JDK`() {
		val source = SourceFile.kotlin("kSource.kt", """
            import javax.lang.model.SourceVersion
            import java.io.File
            
            fun main(addKotlincArgs: Array<String>) {
            	File("")
            }
			""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KSourceKt")
	}

	@Test
	fun `Kotlin can not access JDK`() {
		val source = SourceFile.kotlin("kSource.kt", """
            import javax.lang.model.SourceVersion
            import java.io.File
            
            fun main(addKotlincArgs: Array<String>) {
            	File("")
            }
			""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			jdkHome = null

		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).containsIgnoringCase("unresolved reference: java")
	}

	@Test
	fun `can compile Kotlin without JDK`() {
		val source = SourceFile.kotlin("kSource.kt", "class KClass")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			jdkHome = null
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "KClass")
	}

	@Test
	fun `Java can access JDK`() {
		val source = SourceFile.java("JSource.java", """
            import javax.lang.model.SourceVersion;
            import java.io.File;
            
            class JSource {
            	File foo() {
            		return new File("");
            	}
            }
			""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "JSource")
	}

	@Test
	fun `Java can not access JDK`() {
		val source = SourceFile.java("JSource.java", """
		    import javax.lang.model.SourceVersion;
		    import java.io.File;
		    
		    class JSource {
		    	File foo() {
		    		return new File("");
		    	}
		    }
			""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			jdkHome = null
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).contains("Unable to find package java.lang")
	}

	@Test
	fun `Java inherits classpath`() {
		val source = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = KotlinCompilationTests.InheritedClass.class.getName();
				}
			}
				""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java doesn't inherit classpath`() {
		val source = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = KotlinCompilationTests.InheritedClass.class.getName();
				}
			}
				""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			inheritClassPath = false
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).contains("package KotlinCompilationTests does not exist")
	}

	@Test
	fun `Kotlin inherits classpath`() {
		val source = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun foo() {
					val s = KotlinCompilationTests.InheritedClass::class.java.name
				}
			}
				""")


		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
	}

	@Test
	fun `Kotlin doesn't inherit classpath`() {
		val source = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun foo() {
					val s = KotlinCompilationTests.InheritedClass::class.java.name
				}
			}
				""")


		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			inheritClassPath = false
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
		assertThat(result.messages).containsIgnoringCase("unresolved reference: KotlinCompilationTests")
	}

	@Test
	fun `Compiled Kotlin class can be loaded`() {
		val source = SourceFile.kotlin("Source.kt", """
			package com.tschuchort.compiletesting
		
			class Source {
				fun helloWorld(): String {
					return "Hello World"
				}
			}
				""")


		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
			annotationProcessors = listOf(object : AbstractProcessor() {
				override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
					return false
				}
			})
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		val clazz = result.classLoader.loadClass("com.tschuchort.compiletesting.Source")
		assertThat(clazz).isNotNull

		val instance = clazz.newInstance()
		assertThat(instance).isNotNull

		assertThat(clazz).hasDeclaredMethods("helloWorld")
		assertThat(clazz.getDeclaredMethod("helloWorld").invoke(instance)).isEqualTo("Hello World")
	}

	@Test
	fun `Compiled Java class can be loaded`() {
		val source = SourceFile.java("Source.java", """
			package com.tschuchort.compiletesting;
		
			public class Source {
				public String helloWorld() {
					return "Hello World";
				}
			}
				""")


		val result = defaultCompilerConfig().apply {
			sources = listOf(source)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)

		val clazz = result.classLoader.loadClass("com.tschuchort.compiletesting.Source")
		assertThat(clazz).isNotNull

		val instance = clazz.newInstance()
		assertThat(instance).isNotNull

		assertThat(clazz).hasDeclaredMethods("helloWorld")
		assertThat(clazz.getDeclaredMethod("helloWorld").invoke(instance)).isEqualTo("Hello World")
	}

	@Test
	fun `Kotlin can access Java class`() {
		val jSource = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {}
			}
				""")

				val kSource = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun bar() {
					JSource().foo()
				}
			}
				""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource, jSource)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java can access Kotlin class`() {
		val jSource = SourceFile.java("JSource.java", """
			package com.tschuchort.compiletesting;
		
			class JSource {
				void foo() {
					String s = (new KSource()).bar();
				}
			}
				""")

				val kSource = SourceFile.kotlin("KSource.kt", """
			package com.tschuchort.compiletesting
		
			class KSource {
				fun bar(): String = "bar"
			}
				""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource, jSource)
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
	}

	@Test
	fun `Java AP sees Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
				}
					""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(javaTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(JavaTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}

	@Test
	fun `Java AP sees Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					""")

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(javaTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(JavaTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Kotlin AP sees Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}

	@Test
	fun `Kotlin AP sees Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.messages)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Given only Java sources, Kotlin sources are generated and compiled`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.messages).contains(KotlinTestProcessor.ON_INIT_MSG)

		val clazz = result.classLoader.loadClass(KotlinTestProcessor.GENERATED_PACKAGE +
				"." + KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME)
		assertThat(clazz).isNotNull
	}

	@Test
	fun `Java can access generated Kotlin class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME};
			
				@ProcessElem
				class JSource {
					void foo() {
						Class<?> c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}.class;
					}
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}")
	}

	@Test
	fun `Java can access generated Java class`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME};
			
				@ProcessElem
				class JSource {
					void foo() {
						Class<?> c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}.class;
					}
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.JSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}")
	}

	@Test
	fun `Kotlin can access generated Kotlin class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}
			
				@ProcessElem
				class KSource {
					fun foo() {
						val c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}::class.java
					}
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}")
	}

	@Test
	fun `Kotlin can access generated Java class`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
				import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}
			
				@ProcessElem
				class KSource {
					fun foo() {
						val c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}::class.java
					}
				}
				"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertClassLoadable(result, "com.tschuchort.compiletesting.KSource")
		assertClassLoadable(result, "${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}")
	}

	@Test
	fun `detects the plugin provided for compilation via pluginClasspaths property`() {
		val result = defaultCompilerConfig().apply {
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
		val result = defaultCompilerConfig().apply {
			sources = listOf(SourceFile.kotlin("kSource.kt", "class KSource"))
			pluginClasspaths = listOf(File("./non-existing-plugin.jar"))
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.INTERNAL_ERROR)
		assertThat(result.messages).contains("non-existing-plugin.jar not found")
	}

	@Test
	fun `Generated Java source is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains(KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME + ".java")
	}

	@Test
	fun `Generated Kotlin source is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains(KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME + ".kt")
	}

	@Test
	fun `Compiled Kotlin class file is among generated files list`() {
		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(kSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains("KSource.class")
	}

	@Test
	fun `Compiled Java class file is among generated files list`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
					void foo() {
					}
				}
					"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource)
			annotationProcessors = listOf(kotlinTestProc)
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.generatedFiles.map { it.name }).contains("JSource.class")
	}

	@Test
	fun `Custom plugin receives CLI argument`() {
	    val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting;
				class KSource()
			""".trimIndent()
		)

		val cliProcessor = spy(object : CommandLineProcessor {
			override val pluginId = "myPluginId"
			override val pluginOptions = listOf(CliOption("test_option_name", "", ""))
		})

		val result = defaultCompilerConfig().apply {
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

	@Test
	fun `Output directory contains compiled class files`() {
		val jSource = SourceFile.java(
			"JSource.java", """
				package com.tschuchort.compiletesting;
			
				@ProcessElem
				class JSource {
					void foo() {
					}
				}
					"""
		)

		val kSource = SourceFile.kotlin(
			"KSource.kt", """
				package com.tschuchort.compiletesting
			
				@ProcessElem
				class KSource {
					fun foo() {}
				}
				"""
		)

		val result = defaultCompilerConfig().apply {
			sources = listOf(jSource, kSource)
			annotationProcessors = emptyList()
			inheritClassPath = true
		}.compile()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.outputDirectory.listFilesRecursively().map { it.name })
				.contains("JSource.class", "KSource.class")
	}
	
	class InheritedClass {}
}
