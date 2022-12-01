plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenPublish)
}

dependencies {
    compileOnly(libs.kotlin.compilerEmbeddable)
    api(projects.core)
    compileOnly(libs.ksp.api)
    implementation(libs.ksp)
    testImplementation(libs.ksp.api)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.assertJ)
}
