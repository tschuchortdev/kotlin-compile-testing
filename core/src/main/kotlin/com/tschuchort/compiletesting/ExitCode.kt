package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.ExitCode as CliExitCode

/** ExitCode of the entire Kotlin compilation process */
enum class ExitCode {
    OK, INTERNAL_ERROR, COMPILATION_ERROR, SCRIPT_EXECUTION_ERROR
}

internal fun convertKotlinExitCode(code: CliExitCode) = when(code) {
    CliExitCode.OK -> ExitCode.OK
    CliExitCode.INTERNAL_ERROR -> ExitCode.INTERNAL_ERROR
    CliExitCode.COMPILATION_ERROR -> ExitCode.COMPILATION_ERROR
    CliExitCode.SCRIPT_EXECUTION_ERROR -> ExitCode.SCRIPT_EXECUTION_ERROR
}
