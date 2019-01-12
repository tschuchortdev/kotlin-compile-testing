package com.tschuchort.compiletesting

import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import javax.annotation.processing.Processor

@Suppress("MemberVisibilityCanBePrivate")
class KotlinCompilationTests {
	@Rule @JvmField val temporaryFolder = TemporaryFolder()

	val kotlinTestProcService = KotlinCompilation.Service(Processor::class, KotlinTestProcessor::class)
	val javaTestProcService = KotlinCompilation.Service(Processor::class, JavaTestProcessor::class)

	@Test
	fun `runs with only kotlin sources`() {
		val result = compilationPreset().copy(
			sources = listOf(KotlinCompilation.SourceFile("kSource.kt", ""))
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `runs with only java sources`() {
		val result = compilationPreset().copy(
			sources = listOf(KotlinCompilation.SourceFile("JSource.java", "class JSource {}"))
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `runs with no sources`() {
		val result = compilationPreset().copy(
			sources = emptyList()
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}


	@Test
	fun `Kotlin can access JDK`() {
		val source = KotlinCompilation.SourceFile("kSource.kt", """
    |import javax.lang.model.SourceVersion
    |import java.io.File
    |
    |fun main(addKotlincArgs: Array<String>) {
    |	File("")
    |}
			""".trimMargin())

		val result = compilationPreset().copy(
			sources = listOf(source)
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Kotlin can not access JDK`() {
		val source = KotlinCompilation.SourceFile("kSource.kt", """
    |import javax.lang.model.SourceVersion
    |import java.io.File
    |
    |fun main(addKotlincArgs: Array<String>) {
    |	File("")
    |}
			""".trimMargin())

		val result = compilationPreset().copy(
			sources = listOf(source),
			jdkHome = null
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
	}

	@Test
	fun `can compile Kotlin without JDK`() {
		val source = KotlinCompilation.SourceFile("kSource.kt", """
    |fun foo() {
    |}
			""".trimMargin())

		val result = compilationPreset().copy(
			sources = listOf(source),
			jdkHome = null
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Java can access JDK`() {
		val source = KotlinCompilation.SourceFile("JSource.java", """
    |import javax.lang.model.SourceVersion;
    |import java.io.File;
    |
    |class JSource {
    |	File foo() {
    |		return new File("");
    |	}
    |}
			""".trimMargin())

		val result = compilationPreset().copy(
			sources = listOf(source)
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Java can not access JDK`() {
		val source = KotlinCompilation.SourceFile("JSource.java", """
		|import javax.lang.model.SourceVersion;
		|import java.io.File;
		|
		|class JSource {
		|	File foo() {
		|		return new File("");
		|	}
		|}
			""".trimMargin())

		val result = compilationPreset().copy(
			sources = listOf(source),
			jdkHome = null
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
	}

	@Test
	fun `Java inherits classpath`() {
		val source = KotlinCompilation.SourceFile("Source.java", """
    package com.tschuchort.compiletesting;

    class Source {
    	void foo() {
    		String s = KotlinCompilationTests.InheritedClass.class.getName();
    	}
    }
    	""".trimIndent())

		val result = compilationPreset().copy(
			sources = listOf(source),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Kotlin inherits classpath`() {
		val source = KotlinCompilation.SourceFile("Source.kt", """
    package com.tschuchort.compiletesting

    class Source {
    	fun foo() {
    		val s = KotlinCompilationTests.InheritedClass::class.java.name
    	}
    }
		""".trimIndent())


		val result = compilationPreset().copy(
			sources = listOf(source),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Compiled Kotlin class can be loaded`() {
		val source = KotlinCompilation.SourceFile("Source.kt", """
    package com.tschuchort.compiletesting

    class Source {
    	fun helloWorld(): String {
    		return "Hello World"
    	}
    }
		""".trimIndent())


		val result = compilationPreset().copy(
			sources = listOf(source)
		).compile_()

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
		val source = KotlinCompilation.SourceFile("Source.java", """
    package com.tschuchort.compiletesting;

    public class Source {
    	public String helloWorld() {
    		return "Hello World";
    	}
    }
		""".trimIndent())


		val result = compilationPreset().copy(
			sources = listOf(source)
		).compile_()

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
		val jSource = KotlinCompilation.SourceFile("JSource.java", """
    package com.tschuchort.compiletesting;

    class JSource {
    	void foo() {}
    }
		""".trimIndent())

		val kSource = KotlinCompilation.SourceFile("KSource.kt", """
    package com.tschuchort.compiletesting

    class KSource {
    	fun bar() {
    		JSource().foo()
    	}
    }
		""".trimIndent())

		val result = compilationPreset().copy(
			sources = listOf(kSource, jSource)
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Java can access Kotlin class`() {
		val jSource = KotlinCompilation.SourceFile("JSource.java", """
    package com.tschuchort.compiletesting;

    class JSource {
    	void foo() {
    		String s = (new KSource()).bar();
    	}
    }
		""".trimIndent())

		val kSource = KotlinCompilation.SourceFile("KSource.kt", """
    package com.tschuchort.compiletesting

    class KSource {
    	fun bar(): String = "bar"
    }
		""".trimIndent())

		val result = compilationPreset().copy(
			sources = listOf(kSource, jSource)
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Java AP sees Kotlin class`() {
		val kSource = KotlinCompilation.SourceFile(
			"KSource.kt", """
    package com.tschuchort.compiletesting

	@ProcessElem
    class KSource {
    }
		""".trimIndent())

		val result = compilationPreset().copy(
			sources = listOf(kSource),
			services = listOf(javaTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.systemOut).contains(JavaTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.systemOut)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}

	@Test
	fun `Java AP sees Java class`() {
		val jSource = KotlinCompilation.SourceFile(
			"JSource.java", """
    package com.tschuchort.compiletesting;

	@ProcessElem
    class JSource {
    }
		""".trimIndent())

		val result = compilationPreset().copy(
			sources = listOf(jSource),
			services = listOf(javaTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.systemOut).contains(JavaTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.systemOut)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Kotlin AP sees Kotlin class`() {
		val kSource = KotlinCompilation.SourceFile(
			"KSource.kt", """
    package com.tschuchort.compiletesting

	@ProcessElem
    class KSource {
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(kSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.systemOut).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.systemOut)).anyMatch {
			it.elementSimpleName == "KSource"
		}
	}


	@Test
	fun `Kotlin AP sees Java class`() {
		val jSource = KotlinCompilation.SourceFile(
			"JSource.kt", """
    package com.tschuchort.compiletesting;

	@ProcessElem
    class JSource {
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(jSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.systemOut).contains(KotlinTestProcessor.ON_INIT_MSG)

		assertThat(ProcessedElemMessage.parseAllIn(result.systemOut)).anyMatch {
			it.elementSimpleName == "JSource"
		}
	}

	@Test
	fun `Given only Java sources, Kotlin sources are generated and compiled`() {
		val jSource = KotlinCompilation.SourceFile(
			"JSource.java", """
    package com.tschuchort.compiletesting;

	@ProcessElem
    class JSource {
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(jSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
		assertThat(result.systemOut).contains(KotlinTestProcessor.ON_INIT_MSG)

		val clazz = result.classLoader.loadClass(KotlinTestProcessor.GENERATED_PACKAGE +
				"." + KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME)
		assertThat(clazz).isNotNull
	}

	@Test
	fun `Java can access generated Kotlin class`() {
		val jSource = KotlinCompilation.SourceFile(
			"JSource.java", """
    package com.tschuchort.compiletesting;
    import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME};

	@ProcessElem
    class JSource {
    	void foo() {
    		Class<?> c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}.class;
		}
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(jSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Java can access generated Java class`() {
		val jSource = KotlinCompilation.SourceFile(
			"JSource.java", """
    package com.tschuchort.compiletesting;
    import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME};

	@ProcessElem
    class JSource {
    	void foo() {
    		Class<?> c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}.class;
		}
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(jSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Kotlin can access generated Kotlin class`() {
		val kSource = KotlinCompilation.SourceFile(
			"KSource.kt", """
    package com.tschuchort.compiletesting
    import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}

	@ProcessElem
    class KSource {
    	fun foo() {
    		val c = ${KotlinTestProcessor.GENERATED_KOTLIN_CLASS_NAME}::class.java
		}
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(kSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}

	@Test
	fun `Kotlin can access generated Java class`() {
		val kSource = KotlinCompilation.SourceFile(
			"KSource.kt", """
    package com.tschuchort.compiletesting
    import ${KotlinTestProcessor.GENERATED_PACKAGE}.${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}

	@ProcessElem
    class KSource {
    	fun foo() {
    		val c = ${KotlinTestProcessor.GENERATED_JAVA_CLASS_NAME}::class.java
		}
    }
		""".trimIndent()
		)

		val result = compilationPreset().copy(
			sources = listOf(kSource),
			services = listOf(kotlinTestProcService),
			inheritClassPath = true
		).compile_()

		assertThat(result.exitCode).isEqualTo(ExitCode.OK)
	}



	private fun compilationPreset(): KotlinCompilation {
		val jdkHome = getJdkHome()

		return KotlinCompilation(
			workingDir = temporaryFolder.root,
			jdkHome = jdkHome,
			toolsJar = if(isJdk9OrLater())
				null
			else
				File(jdkHome, "lib\\tools.jar"),
			inheritClassPath = false,
			skipRuntimeVersionCheck = true,
			correctErrorTypes = true,
			verbose = true,
			reportOutputFiles = true
		)
	}

	private fun KotlinCompilation.compile_() = run {
		val systemOutBuffer = Buffer()
		val result = copy(systemOut = PrintStream(TeeOutputStream(System.out, systemOutBuffer.outputStream())))
			.compile()

		return@run object {
			val exitCode = result.exitCode
			val classLoader = URLClassLoader(arrayOf(result.outputDirectory.toURI().toURL()),
				this::class.java.classLoader)
			val systemOut = systemOutBuffer.readUtf8()
		}
	}

	class InheritedClass {}
}