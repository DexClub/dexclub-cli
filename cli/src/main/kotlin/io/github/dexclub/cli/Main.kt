package io.github.dexclub.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    DexKitCliBootstrap.configureNativeLibraryDir()
    val exitCode = CliApp().run(
        argv = args.toList(),
        stdout = System.out,
        stderr = System.err,
    )
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
