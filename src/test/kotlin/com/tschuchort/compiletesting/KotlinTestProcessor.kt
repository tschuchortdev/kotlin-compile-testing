package com.tschuchort.compiletesting

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec as JavaTypeSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

annotation class ProcessElem

data class ProcessedElemMessage(val elementSimpleName: String) {
	fun print() = MSG_PREFIX + elementSimpleName + MSG_SUFFIX

	init {
	    require(elementSimpleName.isNotEmpty())
	}

	companion object {
		private const val MSG_PREFIX = "processed element{"
		private const val MSG_SUFFIX = "}"

	    fun parseAllIn(s: String): List<ProcessedElemMessage> {
			val pattern = Regex(Regex.escape(MSG_PREFIX) + "(.+)?" + Regex.escape(MSG_SUFFIX))
			return pattern.findAll(s)
				.map { match ->
					ProcessedElemMessage(match.destructured.component1())
				}.toList()
		}
	}
}

class KotlinTestProcessor : AbstractProcessor() {
	companion object {
		private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
		private const val GENERATE_KOTLIN_CODE_OPTION = "generate.kotlin.value"
		private const val GENERATE_ERRORS_OPTION = "generate.error"
		private const val FILE_SUFFIX_OPTION = "suffix"
		const val ON_INIT_MSG = "kotlin processor init"
		const val GENERATED_PACKAGE = "com.tschuchort.compiletesting"
		const val GENERATED_JAVA_CLASS_NAME = "KotlinGeneratedJavaClass"
		const val GENERATED_KOTLIN_CLASS_NAME = "KotlinGeneratedKotlinClass"
	}

	private val kaptKotlinGeneratedDir by lazy { processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] }

	override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()
	override fun getSupportedOptions() = setOf(
		KAPT_KOTLIN_GENERATED_OPTION_NAME,
		GENERATE_KOTLIN_CODE_OPTION,
		GENERATE_ERRORS_OPTION
	)
	override fun getSupportedAnnotationTypes(): Set<String> = setOf(ProcessElem::class.java.canonicalName)

    override fun init(processingEnv: ProcessingEnvironment) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, ON_INIT_MSG)
        super.init(processingEnv)
    }

	override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
		processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "kotlin processor was called")

		for (annotatedElem in roundEnv.getElementsAnnotatedWith(ProcessElem::class.java)) {
			processingEnv.messager.printMessage(Diagnostic.Kind.WARNING,
				ProcessedElemMessage(annotatedElem.simpleName.toString()).print())
		}

		if(annotations.isEmpty()) {
			FileSpec.builder(GENERATED_PACKAGE, GENERATED_KOTLIN_CLASS_NAME + ".kt")
					.addType(
						TypeSpec.classBuilder(GENERATED_KOTLIN_CLASS_NAME).build()
					).build()
					.let { writeKotlinFile(it) }

			JavaFile.builder(GENERATED_PACKAGE, JavaTypeSpec.classBuilder(GENERATED_JAVA_CLASS_NAME).build())
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