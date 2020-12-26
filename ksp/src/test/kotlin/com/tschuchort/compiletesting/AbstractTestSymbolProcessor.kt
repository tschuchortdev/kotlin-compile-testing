package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor

/**
 * Helper class to write tests, only used in Ksp Compile Testing tests, not a public API.
 */
internal open class AbstractTestSymbolProcessor : SymbolProcessor {
    protected lateinit var codeGenerator: CodeGenerator
    protected lateinit var logger: KSPLogger
    override fun finish() {
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator, logger: KSPLogger) {
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver) {
    }
}
