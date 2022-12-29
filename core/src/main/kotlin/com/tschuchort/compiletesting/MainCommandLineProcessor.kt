package com.tschuchort.compiletesting

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.*

@ExperimentalCompilerApi
@AutoService(CommandLineProcessor::class)
internal class MainCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = Companion.pluginId

    override val pluginOptions: Collection<AbstractCliOption>
        get() = threadLocalParameters.get()?.pluginOptions
            ?: emptyList<AbstractCliOption>().also {
                // Handle unset parameters gracefully because this plugin may be accidentally called by other tools that
                // discover it on the classpath (for example the kotlin jupyter kernel).
                System.err.println("WARNING: MainCommandLineProcessor::pluginOptions accessed before thread local parameters have been set")
            }

    companion object {
        const val pluginId = "com.tschuchort.compiletesting.maincommandlineprocessor"

        /** This CommandLineProcessor is instantiated by K2JVMCompiler using
         *  a service locator. So we can't just pass parameters to it easily.
         *  Instead, we need to use a thread-local global variable to pass
         *  any parameters that change between compilations
         */
        val threadLocalParameters: ThreadLocal<ThreadLocalParameters> = ThreadLocal()

        private fun encode(str: String) = str //Base64.getEncoder().encodeToString(str.toByteArray()).replace('=', '%')

        private fun decode(str: String) = str // String(Base64.getDecoder().decode(str.replace('%', '=')))

        fun encodeForeignOptionName(processorPluginId: PluginId, optionName: OptionName)
                = encode(processorPluginId) + ":" + encode(optionName)

        fun decodeForeignOptionName(str: String): Pair<PluginId, OptionName> {
            return Regex("(.*?):(.*)").matchEntire(str)?.groupValues?.let { (_, encodedPluginId, encodedOptionName) ->
                Pair(decode(encodedPluginId), decode(encodedOptionName))
            }
                ?: error("Could not decode foreign option name: '$str'.")
        }
    }

    class ThreadLocalParameters(cliProcessors: List<CommandLineProcessor>) {
        val cliProcessorsByPluginId: Map<PluginId, List<CommandLineProcessor>>
                = cliProcessors.groupBy(CommandLineProcessor::pluginId)

        val optionByProcessorAndName: Map<Pair<CommandLineProcessor, OptionName>, AbstractCliOption>
                = cliProcessors.flatMap { cliProcessor ->
            cliProcessor.pluginOptions.map { option ->
                Pair(Pair(cliProcessor, option.optionName), option)
            }
        }.toMap()

        val pluginOptions = cliProcessorsByPluginId.flatMap { (processorPluginId, cliProcessors) ->
            cliProcessors.flatMap { cliProcessor ->
                cliProcessor.pluginOptions.map { option ->
                    CliOption(
                        encodeForeignOptionName(processorPluginId, option.optionName),
                        option.valueDescription,
                        option.description,
                        option.required,
                        option.allowMultipleOccurrences
                    )
                }
            }
        }
    }

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        // Handle unset parameters gracefully because this plugin may be accidentally called by other tools that
        // discover it on the classpath (for example the kotlin jupyter kernel).
        if (threadLocalParameters.get() == null) {
            System.err.println("WARNING: MainCommandLineProcessor::processOption accessed before thread local parameters have been set")
            return
        }

        val (foreignPluginId, foreignOptionName) = decodeForeignOptionName(option.optionName)
        val params = threadLocalParameters.get()

        params.cliProcessorsByPluginId[foreignPluginId]?.forEach { cliProcessor ->
            cliProcessor.processOption(
                params.optionByProcessorAndName[Pair(cliProcessor, foreignOptionName)]
                    ?: error("Could not get AbstractCliOption for option '$foreignOptionName'"),
                value, configuration
            )
        }
            ?: error("No CommandLineProcessor given for option '$foreignOptionName' for plugin ID $foreignPluginId")
    }
}