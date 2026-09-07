package io.github.dexclub.cli

import io.github.dexclub.core.app.createDefaultAppServices
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliHelpCommandTest {
    @Test
    fun parserErrorsUseExitCodeOneAndUsageOutput() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-empty").toString() },
        )

        val output = runCli(app, listOf("status", "--json", "extra"))
        assertEquals(1, output.exitCode)
        assertTrue(output.stderr.contains("Error: positional arguments must appear before options"))
        assertTrue(output.stderr.contains("Usage:"))
    }

    @Test
    fun helpCommandPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-help").toString() },
        )

        val output = runCli(app, listOf("help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub"))
        assertTrue(output.stdout.contains("Lifecycle Commands:"))
        assertTrue(output.stdout.contains("Run 'cli help <command>' for command-specific details."))
    }

    @Test
    fun emptyArgvPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-empty-help").toString() },
        )

        val output = runCli(app, emptyList())
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub"))
        assertTrue(output.stdout.contains("Resource Commands:"))
    }

    @Test
    fun topLevelHelpFlagPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-help-flag").toString() },
        )

        val output = runCli(app, listOf("--help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub"))
        assertTrue(output.stdout.contains("Version:"))
        assertTrue(output.stdout.contains("Dex Analysis Commands:"))
    }

    @Test
    fun versionFlagPrintsBuildVersion() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-version").toString() },
        )

        val output = runCli(app, listOf("--version"))
        assertEquals(0, output.exitCode)
        assertEquals(CliBuildInfo.VERSION, output.stdout.trim())
    }

    @Test
    fun commandHelpFlagPrintsCommandHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-command-help").toString() },
        )

        val output = runCli(app, listOf("find-method", "--help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("Command:"))
        assertTrue(output.stdout.contains("find-method"))
        assertTrue(output.stdout.contains("Usage:"))
        assertTrue(output.stdout.contains(CliUsages.findMethod))
    }

    @Test
    fun helpCommandPrintsCommandHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-help-command").toString() },
        )

        val output = runCli(app, listOf("help", "manifest"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("Command:"))
        assertTrue(output.stdout.contains("manifest"))
        assertTrue(output.stdout.contains(CliUsages.manifest))
    }

    @Test
    fun resourceCommandHelpDocumentsStructuredValueContract() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-resource-help").toString() },
        )

        val getValue = runCli(app, listOf("help", "get-res-value"))
        assertEquals(0, getValue.exitCode)
        assertTrue(getValue.stdout.contains("configuration variants"))
        assertTrue(getValue.stdout.contains("typed value or a generic bag"))

        val findValues = runCli(app, listOf("help", "find-res-values"))
        assertEquals(0, findValues.exitCode)
        assertTrue(findValues.stdout.contains("packageName, qualifier, valueKind, and matchTarget"))
        assertTrue(findValues.stdout.contains("decoded_value, raw_data, reference, bag_key, or any"))
    }

    @Test
    fun unknownCommandHintsHelp() {
        val app = CliApp(
            services = createDefaultAppServices(),
            cwdProvider = { createTempDirectory("dexclub-unknown-command").toString() },
        )

        val output = runCli(app, listOf("wat"))
        assertEquals(1, output.exitCode)
        assertTrue(output.stderr.contains("Error: unknown command: wat"))
        assertTrue(output.stderr.contains("Run 'cli help' to see available commands."))
    }
}
