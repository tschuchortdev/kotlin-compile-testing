package com.tschuchort.compiletesting

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

class FakeCompilerPluginRegistrar(
    override val supportsK2: Boolean = false,
) : CompilerPluginRegistrar() {
    private var isRegistered = false

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        isRegistered = true
    }

    fun assertRegistered() {
        if(!isRegistered) {
            throw AssertionError("FakeCompilerPluginRegistrar was not registered")
        }
    }
}