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

// Would be nice if SymbolProcessorProvider was a fun interface
internal fun processorProviderOf(
    body: (
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) -> SymbolProcessor
): SymbolProcessorProvider {
    return object : SymbolProcessorProvider {
        override fun create(
            options: Map<String, String>,
            kotlinVersion: KotlinVersion,
            codeGenerator: CodeGenerator,
            logger: KSPLogger
        ): SymbolProcessor {
            return body(options, kotlinVersion, codeGenerator, logger)
        }
    }
}
