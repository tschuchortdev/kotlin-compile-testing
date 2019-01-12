package com.tschuchort.compiletesting;

import com.squareup.javapoet.JavaFile;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.TypeSpec;
import kotlin.text.Charsets;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaTestProcessor extends AbstractProcessor {

    public static String ON_INIT_MSG = "java processor init";
    public static String GENERATED_PACKAGE = "com.tschuchort.compiletesting";
    public static String GENERATED_JAVA_CLASS_NAME = "JavaGeneratedJavaClass";
    public static String GENERATED_KOTLIN_CLASS_NAME = "JavaGeneratedKotlinClass";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
         LinkedHashSet<String> set = new LinkedHashSet<>();
         set.add(ProcessElem.class.getCanonicalName());
         return set;
    }

    @Override
    public Set<String> getSupportedOptions() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("kapt.kotlin.generated");
        return set;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, ON_INIT_MSG);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "java processor was called");

        for (Element annotatedElem : roundEnv.getElementsAnnotatedWith(ProcessElem.class)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    new ProcessedElemMessage(annotatedElem.getSimpleName().toString()).print());
        }

        if(annotations.isEmpty()) {
            TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(GENERATED_KOTLIN_CLASS_NAME);

            FileSpec fileSpec = FileSpec.builder(GENERATED_PACKAGE, GENERATED_KOTLIN_CLASS_NAME + ".kt")
                    .addType(typeSpecBuilder.build()).build();

            writeKotlinFile(fileSpec, fileSpec.getName(), fileSpec.getPackageName());

            try {
                JavaFile.builder(GENERATED_PACKAGE,
                        com.squareup.javapoet.TypeSpec.classBuilder(GENERATED_JAVA_CLASS_NAME).build())
                        .build().writeTo(processingEnv.getFiler());
            } catch (Exception e) {
            }
        }

        return false;
    }

    private void writeKotlinFile(FileSpec fileSpec, String fileName, String packageName) {
        String kaptKotlinGeneratedDir = processingEnv.getOptions().get("kapt.kotlin.generated");

        String relativePath = packageName.replace('.', File.separatorChar);

        File outputFolder = new File(kaptKotlinGeneratedDir, relativePath);
        outputFolder.mkdirs();

        // finally write to output file
        try {
            (new FileOutputStream(new File(outputFolder, fileName))).write(fileSpec.toString().getBytes(Charsets.UTF_8));
        }
        catch(Exception e) {
        }
    }
}
