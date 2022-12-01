# Kotlin Compile Testing

A library for in-process compilation of Kotlin and Java code, in the spirit of [Google Compile Testing](https://github.com/google/compile-testing). For example, you can use this library to test your annotation processor or compiler plugin.

**NOTE** This project is a fork of the original [tschuchortdev/kotlin-compile-testing](https://github.com/github.com/tschuchortdev/kotlin-compile-testing), which itself started as a fork of [Moshi](https://github.com/square/moshi)'s compile test infra. The goal of this fork is to better track the latest Kotlin releases.

## Use Cases

- Compile Kotlin and Java code in tests
- Test annotation processors
- Test compiler plugins
- Test Kotlin code generation

## Example

Create sources

```Kotlin
class TestEnvClass {}

@Test
fun `test my annotation processor`() {
  val kotlinSource = SourceFile.kotlin(
    "KClass.kt", """
        class KClass {
            fun foo() {
                // Classes from the test environment are visible to the compiled sources
                val testEnvClass = TestEnvClass() 
            }
        }
    """
  )

  val javaSource = SourceFile.java(
    "JClass.java", """
        public class JClass {
            public void bar() {
                // compiled Kotlin classes are visible to Java sources
                KClass kClass = new KClass(); 
            }
	    }
    """
  )
}
```

Configure compilation
```Kotlin
    val result = KotlinCompilation().apply {
        sources = listOf(kotlinSource, javaSource)
        
        // pass your own instance of an annotation processor
        annotationProcessors = listOf(MyAnnotationProcessor()) 

        // pass your own instance of a compiler plugin
        compilerPlugins = listOf(MyComponentRegistrar())
	commandLineProcessors = listOf(MyCommandlineProcessor())
        
        inheritClassPath = true
        messageOutputStream = System.out // see diagnostics in real time
    }.compile()
```

Assert results
```Kotlin
assertThat(result.exitCode).isEqualTo(ExitCode.OK)	

// Test diagnostic output of compiler
assertThat(result.messages).contains("My annotation processor was called") 

// Load compiled classes and inspect generated code through reflection
val kClazz = result.classLoader.loadClass("KClass")
assertThat(kClazz).hasDeclaredMethods("foo")
```

## Features
- Mixed-source sets: Compile Kotlin and Java source files in a single run
- Annotation processing: 
    - Run annotation processors on Kotlin and Java sources
    - Generate Kotlin and Java sources
    - Both Kotlin and Java sources have access to the generated sources
    - Provide your own instances of annotation processors directly to the compiler instead of letting the compiler create them with a service locator
    - Debug annotation processors: Since the compilation runs in the same process as your application, you can easily debug it instead of having to attach your IDE's debugger manually to the compilation process
- Inherit classpath: Compiled sources have access to classes in your application
- Project Jigsaw compatible: Kotlin-Compile-Testing works with JDK 8 as well as JDK 9 and later
- JDK-crosscompilation: Provide your own JDK to compile the code against, instead of using the host application's JDK. This allows you to easily test your code on all JDK versions
- Find dependencies automatically on the host classpath

## Installation <img src="https://i.imgur.com/iV36acM.png" width="23">

The package is available on Maven Central.

Add dependency to your module's `build.gradle` file:

```Groovy
dependencies {
        // ...
	testImplementation("dev.zacsweers.kctfork:core:<version>>")
}
```

## Compatibility

Kotlin-Compile-Testing is compatible with all _local_ compiler versions. It does not matter what compiler you use to compile your project. 

However, if your project or any of its dependencies depend directly on compiler artifacts such as `kotlin-compiler-embeddable` or `kotlin-annotation-processing-embeddable` then they have to be the same version as the one used by Kotlin-Compile-Testing or there will be a transitive dependency conflict.

Because the internal APIs of the Kotlin compiler often change between versions, we can only support one `kotlin-compiler-embeddable` version at a time. 

## Kotlin Symbol Processing API Support
[Kotlin Symbol Processing (KSP)](https://goo.gle/ksp) is a new annotation processing pipeline that builds on top of the
plugin architecture of the Kotlin Compiler, instead of delegating to javac as `kapt` does.

To test KSP processors, you need to use the KSP dependency:

```Groovy
dependencies {
    testImplementation("dev.zacsweers.kctfork:ksp:<version>>")
}
```

This module adds a new function to the `KotlinCompilation` to specify KSP processors:

```Kotlin
class MySymbolProcessorProvider : SymbolProcessorProvider {
    // implementation of the SymbolProcessorProvider from the KSP API
}
val compilation = KotlinCompilation().apply {
    sources = listOf(source)
    symbolProcessorProviders = listOf(MySymbolProcessorProvider())
}
val result = compilation.compile()
```
All code generated by the KSP processor will be written into the `KotlinCompilation.kspSourcesDir` directory.

## Java 16 compatibility

With the release of Java 16 the access control of the new Jigsaw module system is starting to be enforced by the JVM. Unfortunately, this impacts kotlin-compile-testing because KAPT still tries to access classes of javac that are not exported by the jdk.compiler module, leading to errors such as:
```
java.lang.IllegalAccessError: class org.jetbrains.kotlin.kapt3.base.KaptContext (in unnamed module @0x43b6aa9d) cannot access class com.sun.tools.javac.util.Context (in module jdk.compiler) because module jdk.compiler does not export com.sun.tools.javac.util to unnamed module @0x43b6aa9d
```
To mitigate this problem, you have to add the following code to your module's `build.gradle` file:
```groovy
if (JavaVersion.current() >= JavaVersion.VERSION_16) {
    test {
        jvmArgs(
          "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        )
    }
}
```

or for Kotlin DSL

```kotlin
if (JavaVersion.current() >= JavaVersion.VERSION_16) {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        )
    }
}
```
Since the kotlin compilation tests run in the same process as the test runner, these options have to be added manually and can not be set automatically by the kotlin-compile-testing library.

## License

Copyright (C) 2021 Thilo Schuchort

Licensed under the Mozilla Public License 2.0

For custom license agreements contact me directly 
