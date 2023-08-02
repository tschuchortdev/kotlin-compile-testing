package com.tschuchort.compiletesting

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Helper class to write tests, only used in Ksp Compile Testing tests, not a public API.
 */
internal open class AbstractTestSymbolProcessor(
    protected val codeGenerator: CodeGenerator
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        return emptyList()
    }
}