package com.tschuchort.compiletesting

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec as JavaTypeSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

annotation class Marker

class KotlinTestProcessor : AbstractProcessor() {
	companion object {
		const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
		const val GENERATE_KOTLIN_CODE_OPTION = "generate.kotlin.value"
		const val GENERATE_ERRORS_OPTION = "generate.error"
		const val FILE_SUFFIX_OPTION = "suffix"
	}

	private val kaptKotlinGeneratedDir by lazy { processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] }
	private val generateErrors by lazy { processingEnv.options[GENERATE_ERRORS_OPTION] == "true" }
	private val generateKotlinCode by lazy { processingEnv.options[GENERATE_KOTLIN_CODE_OPTION] == "true" }
	private val generatedFilesSuffix by lazy { processingEnv.options[FILE_SUFFIX_OPTION] ?: "Generated"}

	override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()
	override fun getSupportedOptions() = setOf(
		KAPT_KOTLIN_GENERATED_OPTION_NAME,
		GENERATE_KOTLIN_CODE_OPTION,
		GENERATE_ERRORS_OPTION
	)
	override fun getSupportedAnnotationTypes(): Set<String> = setOf(Marker::class.java.canonicalName)

    override fun init(processingEnv: ProcessingEnvironment) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "kotlin processor init")
        super.init(processingEnv)
    }

	override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
		processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "kotlin processor was called")

		if(annotations.isNotEmpty()) {
			FileSpec.builder("com.tschuchort.compiletesting", "KotlinGeneratedKotlinClass.kt")
				.addType(
					TypeSpec.classBuilder("KotlinGeneratedKotlinClass").apply {
						for (annotatedElem in roundEnv.getElementsAnnotatedWith(Marker::class.java)) {
							addFunction(
								FunSpec.builder(annotatedElem.simpleName.toString())
									.build()
							)
						}
					}.build()
				).build()
				.let { writeKotlinFile(it) }

		JavaFile.builder("com.tschuchort.compiletesting", JavaTypeSpec.classBuilder("KotlinGeneratedJavaClass").build())
				.build().writeTo(processingEnv.filer)

		}

		return false
	}

	private fun writeKotlinFile(fileSpec: FileSpec, fileName: String = fileSpec.name, packageName: String = fileSpec.packageName) {
		val relativePath = packageName.replace('.', File.separatorChar)

		val outputFolder = File(kaptKotlinGeneratedDir!!, relativePath)
		outputFolder.mkdirs()

		// finally write to output file
		File(outputFolder, fileName).writeText(fileSpec.toString())
	}
}