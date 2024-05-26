import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.mavenPublish)
}

buildConfig {
    className.set("BuildConfig")
    packageName.set("com.tschuchort.compiletesting")
    sourceSets {
        test {
            buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")
        }
    }
}

/* Multiple variants are offered of the dependencies kotlin-dom-api-compat and kotlin-stdlib-js. Usually, Gradle picks
* them automatically based on what kind of build it is, i.e. it would look for a platform JVM variant for this JVM build.
* Naturally, there is no JVM version for JS libraries. We need to fix the variant attributes manually so the right variant
* will be picked for runtime use in the JS compile tests. */
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module(libs.kotlin.domApiCompat.get().module.toString()))
            .using(variant(module(libs.kotlin.domApiCompat.get().toString())) {
            attributes {
                attribute(Attribute.of("org.jetbrains.kotlin.platform.type", KotlinPlatformType::class.java), KotlinPlatformType.js)
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "kotlin-runtime"))
            }
        })
    }
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module(libs.kotlin.stdlibJs.get().module.toString()))
            .using(variant(module(libs.kotlin.stdlibJs.get().toString())) {
            attributes {
                attribute(Attribute.of("org.jetbrains.kotlin.platform.type", KotlinPlatformType::class.java), KotlinPlatformType.js)
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "kotlin-runtime"))
                attribute(Attribute.of("org.jetbrains.kotlin.js.compiler", String::class.java), "ir")
            }
        })
    }
}

dependencies {
    implementation(libs.autoService)
    ksp(libs.autoService.ksp)

    testImplementation(libs.kotlinpoet)
    testImplementation(libs.javapoet)

    implementation(libs.okio)
    implementation(libs.classgraph)

    // These dependencies are only needed as a "sample" compiler plugin to test that
    // running compiler plugins passed via the pluginClasspath CLI option works
    testRuntimeOnly(libs.kotlin.scriptingCompiler)
    // Include Kotlin/JS standard library in test classpath for auto loading
    testRuntimeOnly(libs.kotlin.stdlibJs)
    testRuntimeOnly(libs.kotlin.domApiCompat)
    testRuntimeOnly(libs.intellij.core)
    testRuntimeOnly(libs.intellij.util)

    // The Kotlin compiler should be near the end of the list because its .jar file includes
    // an obsolete version of Guava
    api(libs.kotlin.compilerEmbeddable)
    api(libs.kotlin.annotationProcessingEmbeddable)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.assertJ)
}

tasks.withType<KotlinCompile>().configureEach {
    val isTest = name.contains("test", ignoreCase = true)
    compilerOptions {
        freeCompilerArgs.addAll(
            // https://github.com/tschuchortdev/kotlin-compile-testing/pull/63
            "-Xno-optimized-callable-references",
            "-Xskip-runtime-version-check",
        )
        if (isTest) {
            freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        }
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
