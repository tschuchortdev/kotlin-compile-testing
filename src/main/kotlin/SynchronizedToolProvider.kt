/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java.javax

import com.tschuchort.compiletesting.isJdk9OrLater

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import javax.tools.JavaCompiler
import javax.tools.ToolProvider


/**
 * ToolProvider has no synchronization internally, so if we don't synchronize from the outside we
 * could wind up loading the compiler classes multiple times from different class loaders.
 */
internal object SynchronizedToolProvider {
    private var getPlatformClassLoaderMethod: Method? = null

    val systemJavaCompiler: JavaCompiler
        get() {
            val compiler = synchronized(ToolProvider::class.java) {
                ToolProvider.getSystemJavaCompiler()
            }

            check(compiler != null) { "System java compiler is null! Are you running without JDK?" }
            return compiler
        }

    // The compiler classes are loaded using the platform class loader in Java 9+.
    val systemToolClassLoader: ClassLoader
        get() {
            if (isJdk9OrLater()) {
                try {
                    return getPlatformClassLoaderMethod!!.invoke(null) as ClassLoader
                } catch (e: IllegalAccessException) {
                    throw RuntimeException(e)
                } catch (e: InvocationTargetException) {
                    throw RuntimeException(e)
                }

            }

            val classLoader: ClassLoader
            synchronized(ToolProvider::class.java) {
                classLoader = ToolProvider.getSystemToolClassLoader()
            }
            return classLoader
        }

    init {
        if (isJdk9OrLater()) {
            try {
                getPlatformClassLoaderMethod = ClassLoader::class.java.getMethod("getPlatformClassLoader")
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }

        }
    }
}