package com.tschuchort.compiletesting

import org.jetbrains.kotlin.cli.common.ExitCode

/** ExitCode of the entire Kotlin compilation process */
enum class ExitCode {
    OK, INTERNAL_ERROR, COMPILATION_ERROR, SCRIPT_EXECUTION_ERROR;

}
fun convertKotlinExitCode(code: org.jetbrains.kotlin.cli.common.ExitCode) : com.tschuchort.compiletesting.ExitCode = when (code) {
    ExitCode.OK -> com.tschuchort.compiletesting.ExitCode.OK
    ExitCode.INTERNAL_ERROR -> com.tschuchort.compiletesting.ExitCode.INTERNAL_ERROR
    ExitCode.COMPILATION_ERROR -> com.tschuchort.compiletesting.ExitCode.COMPILATION_ERROR
    ExitCode.SCRIPT_EXECUTION_ERROR -> com.tschuchort.compiletesting.ExitCode.SCRIPT_EXECUTION_ERROR
}