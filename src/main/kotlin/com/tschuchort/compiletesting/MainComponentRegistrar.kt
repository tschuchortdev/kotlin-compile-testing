/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tschuchort.compiletesting

import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kapt3.base.incremental.IncrementalProcessor

internal class MainComponentRegistrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val parameters = threadLocalParameters.get()
        KaptComponentRegistrar(parameters.processors,parameters.kaptOptions).registerProjectComponents(project,configuration)

        parameters.compilerPlugins.forEach { componentRegistrar->
            componentRegistrar.registerProjectComponents(project,configuration)
        }
    }

    companion object {
        /** This kapt compiler plugin is instantiated by K2JVMCompiler using
         *  a service locator. So we can't just pass parameters to it easily.
         *  Instead we need to use a thread-local global variable to pass
         *  any parameters that change between compilations
         */
        val threadLocalParameters: ThreadLocal<Parameters> = ThreadLocal()
    }

    data class Parameters(
        val processors: List<IncrementalProcessor>,
        val kaptOptions: KaptOptions.Builder,
        val compilerPlugins: List<ComponentRegistrar>
        )
}