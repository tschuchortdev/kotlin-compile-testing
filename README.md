<img src="https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Kotlin-logo.svg/512px-Kotlin-logo.svg.png" align="right"
     title="Kotlin Logo" width="120">

# Kotlin Compile Testing
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.tschuchortdev/kotlin-compile-testing/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.tschuchortdev/kotlin-compile-testing)
[![](https://jitpack.io/v/tschuchortdev/kotlin-compile-testing.svg)](https://jitpack.io/#tschuchortdev/kotlin-compile-testing)
![GitHub](https://img.shields.io/github/license/tschuchortdev/kotlin-compile-testing.svg?color=green&style=popout)
![Maintenance](https://img.shields.io/maintenance/yes/2020.svg?style=popout)
[![Generic badge](https://img.shields.io/badge/contributions-welcome-green.svg)](https://shields.io/)
[![Build Status](https://travis-ci.com/tschuchortdev/kotlin-compile-testing.svg?branch=master)](https://travis-ci.com/tschuchortdev/kotlin-compile-testing)
[![Build status](https://ci.appveyor.com/api/projects/status/jj639rc6whehaf9o?svg=true)](https://ci.appveyor.com/project/tschuchortdev/kotlin-compile-testing)

A library for in-process compilation of Kotlin and Java code, in the spirit of [Google Compile Testing](https://github.com/google/compile-testing). For example, you can use this library to test your annotation processor or compiler plugin.

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
    val kotlinSource = SourceFile.kotlin("KClass.kt", """
        class KClass {
            fun foo() {
                // Classes from the test environment are visible to the compiled sources
                val testEnvClass = TestEnvClass() 
            }
        }
    """)   
      
    val javaSource = SourceFile.java("JClass.java", """
        public class JClass {
            public void bar() {
                // compiled Kotlin classes are visible to Java sources
                KClass kClass = new KClass(); 
            }
    """)
```
Configure compilation
```Kotlin
    val result = KotlinCompilation().apply {
        sources = listOf(kotlinSource, javaSource)
        
        // pass your own instance of an annotation processor
        annotationProcessors = listOf(MyAnnotationProcessor()) 

        // pass your own instance of a compiler plugin
        compilerPlugins = listOf(MyComponentRegistrar())
	commandlineProcessors = listOf(MyCommandlineProcessor())
        
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
}
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

The package is available on mavenCentral and jitpack.

Add dependency to your module's `build.gradle` file:

```Groovy
dependencies {
        // ...
	implementation 'com.github.tschuchortdev:kotlin-compile-testing:1.2.9'
}
```

<img src="https://emojipedia-us.s3.dualstack.us-west-1.amazonaws.com/thumbs/120/whatsapp/186/white-medium-star_2b50.png" width="23"> Remember to leave a star if you found it useful <img src="https://emojipedia-us.s3.dualstack.us-west-1.amazonaws.com/thumbs/120/whatsapp/186/white-medium-star_2b50.png" width="23">

## Compatible Compiler Versions

Kotlin-Compile-Testing is compatible with all _local_ compiler versions. It does not matter what compiler you use to compile your project. 

However, if your project or any of its dependencies depend directly on compiler artifacts such as `kotlin-compiler-embeddable` or `kotlin-annotation-processing-embeddable` then they have to be the same version as the one used by Kotlin-Compile-Testing or there will be a transitive dependency conflict.

- Current `kotlin-compiler-embeddable` version: `1.3.72`

Because the internal APIs of the Kotlin compiler often change between versions, we can only support one `kotlin-compiler-embeddable` version at a time. 

## Kotlin Symbol Processing API Support
[Kotlin Symbol Processing (KSP)](https://goo.gle/ksp) is a new annotation processing pipeline that builds on top of the
plugin architecture of the Kotlin Compiler, instead of delegating to javac as `kapt` does.

**Note:** KSP is currently in active development and requires Kotlin 1.4 hence its support is kept separate from the
main project until it becomes stable.

To test KSP processors, you need to add a dependency to the ksp module:

```Groovy
dependencies {
    implementation 'com.github.tschuchortdev:kotlin-compile-testing:1.2.9'
    implementation 'com.github.tschuchortdev:kotlin-compile-testing-ksp:1.2.9'
}
```

This module adds a new function to the `KotlinCompilation` to specify KSP processors:

```Kotlin
class MySymbolProcessor : SymbolProcessor {
    // implementation of the SymbolProcessor from the KSP API
}
val compilation = KotlinCompilation().apply {
    sources = listOf(source)
    symbolProcessors = listOf(MySymbolProcessor())
}
val result = compilation.compile()
```
All code generated by the KSP processor will be written into the `KotlinCompilation.kspSourcesDir` directory.


## Projects that use Kotlin-Compile-Testing

- [square/moshi](https://github.com/square/moshi)
- [uber/motif](https://github.com/uber/motif)
- [arrow-kt/arrow-meta](https://github.com/arrow-kt/arrow-meta)
- [foso/mpapt](https://github.com/foso/mpapt)
- [kotlintest/kotlintest](https://github.com/kotlintest/kotlintest)
- [bnorm/kotlin-power-assert](https://github.com/bnorm/kotlin-power-assert)
- [JakeWharton/confundus](https://github.com/JakeWharton/confundus)
- [kotest/kotest](https://github.com/kotest/kotest)
- [ZacSweers/aak](https://github.com/ZacSweers/aak)
- [apollographql/apollo-android](https://github.com/apollographql/apollo-android)
- [patxibocos/poetimizely](https://github.com/patxibocos/poetimizely)
- your project...

## License

Copyright (C) 2019 Thilo Schuchort

Licensed under the Mozilla Public License 2.0

For custom license agreements contact me directly 
