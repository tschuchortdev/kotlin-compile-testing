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

dependencies {
    implementation(libs.autoService)
    ksp(libs.autoService.ksp)

    testImplementation(libs.kotlinpoet)
    testImplementation(libs.javapoet)

    implementation(libs.okio)
    implementation(libs.classgraph)

    // This dependency is only needed as a "sample" compiler plugin to test that
    // running compiler plugins passed via the pluginClasspath CLI option works
    testRuntimeOnly(libs.kotlin.scriptingCompiler)

    // Include Kotlin/JS standard library in test classpath for auto loading
    testRuntimeOnly(libs.kotlin.stdlibJs)

    // The Kotlin compiler should be near the end of the list because its .jar file includes
    // an obsolete version of Guava
    api(libs.kotlin.compilerEmbeddable)
    implementation(libs.kotlin.annotationProcessingEmbeddable)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.assertJ)
}

tasks.withType<KotlinCompile>().configureEach {
    // https://github.com/tschuchortdev/kotlin-compile-testing/pull/63
    kotlinOptions.freeCompilerArgs += "-Xno-optimized-callable-references"
    kotlinOptions.freeCompilerArgs += "-Xskip-runtime-version-check"
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
