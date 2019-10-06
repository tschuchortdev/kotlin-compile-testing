package com.tschuchort.compiletesting

import de.jensklingenberg.mpapt.common.MpAptProject
import de.jensklingenberg.mpapt.model.AbstractProcessor
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension

class MpAptTestComponentRegistrar(val abstractProcessor: AbstractProcessor) :ComponentRegistrar{
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val mpapt = MpAptProject(abstractProcessor,configuration)
        StorageComponentContainerContributor.registerExtension(project,mpapt)
        ClassBuilderInterceptorExtension.registerExtension(project,mpapt)
        JsSyntheticTranslateExtension.registerExtension(project,mpapt)

    }

} 