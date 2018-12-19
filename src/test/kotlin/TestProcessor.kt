package com.tschuchort.kotlinelements

import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

annotation class InspectionRoot

class TestProcessor : AbstractProcessor() {

	override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

	override fun getSupportedAnnotationTypes(): Set<String> {
		return setOf(InspectionRoot::class.java.canonicalName)
	}

    override fun init(processingEnv: ProcessingEnvironment) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "processor init")
        super.init(processingEnv)
    }

	override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
		for(annotatedElem in roundEnv.getElementsAnnotatedWith(InspectionRoot::class.java)) {
		}

        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "processor was called :)")

		return true
	}
}