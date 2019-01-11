package com.tschuchort.compiletesting;

import com.squareup.javapoet.JavaFile;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.FunSpec;
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

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
         LinkedHashSet<String> set = new LinkedHashSet<>();
         set.add(Marker.class.getCanonicalName());
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
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "java processor init");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "java processor was called");

        if(!annotations.isEmpty()) {
            TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder("JavaGeneratedKotlinClass");

            for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(Marker.class)) {
                typeSpecBuilder.addFunction(FunSpec.builder(annotatedElement.getSimpleName().toString()
                        ).build()
                ).build();
            }

            FileSpec fileSpec = FileSpec.builder("com.tschuchort.compiletesting", "JavaGeneratedKotlinClass.kt")
                    .addType(typeSpecBuilder.build())
                    .build();

            writeKotlinFile(fileSpec, fileSpec.getName(), fileSpec.getPackageName());

            try {
                JavaFile.builder("com.tschuchort.compiletesting",
                        com.squareup.javapoet.TypeSpec.classBuilder("JavaGeneratedJavaClass").build())
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
