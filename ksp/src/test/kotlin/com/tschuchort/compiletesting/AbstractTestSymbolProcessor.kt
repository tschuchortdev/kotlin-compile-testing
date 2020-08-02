package com.tschuchort.compiletesting

import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor

/**
 * Helper class to write tests, only used in Ksp Compile Testing tests, not a public API.
 */
internal open class AbstractTestSymbolProcessor : SymbolProcessor {
    protected lateinit var codeGenerator: CodeGenerator
    override fun finish() {
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator, logger: KSPLogger) {
        this.codeGenerator = codeGenerator
    }

    override fun process(resolver: Resolver) {
    }
}
