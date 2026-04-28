package io.github.dexclub.cli

import io.github.dexclub.core.api.shared.createDefaultServices
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.writeText
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliAppTest {
    @Test
    fun initStatusGcAndInspectCommandsRunThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", dexFile.toString()))
        assertEquals(0, initOut.exitCode)
        assertTrue(initOut.stdout.contains("state=healthy"))
        assertTrue(workspaceDir.resolve(".dexclub/workspace.json").exists())

        val statusOut = run(app, listOf("status"))
        assertEquals(0, statusOut.exitCode)
        assertTrue(statusOut.stdout.contains("workspaceId="))
        assertTrue(statusOut.stdout.contains("kind=dex"))

        val targetId = workspaceDir.resolve(".dexclub/targets").toFile().listFiles()!!.single().name
        val cacheFile = workspaceDir.resolve(".dexclub/targets/$targetId/cache/decoded/manifest.json")
        cacheFile.parent.createDirectories()
        cacheFile.writeText("cached")
        val gcOut = run(app, listOf("gc"))
        assertEquals(0, gcOut.exitCode)
        assertTrue(gcOut.stdout.contains("deletedFiles=1"))
        assertTrue(!cacheFile.exists())

        val inspectOut = run(app, listOf("inspect"))
        assertEquals(0, inspectOut.exitCode)
        assertTrue(inspectOut.stdout.contains("dexCount=1"))
        assertTrue(inspectOut.stdout.contains("capabilities=inspect,findClass"))
        assertTrue(!inspectOut.stdout.contains("classCount="))
    }

    @Test
    fun statusUsesBrokenExitCodeWhenInputIsMissing() {
        val workspaceDir = createTempDirectory("dexclub-cli-broken")
        val apkFile = workspaceDir.resolve("app.apk")
        apkFile.writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        run(app, listOf("init", apkFile.toString()))
        apkFile.deleteExisting()

        val statusOut = run(app, listOf("status"))
        assertEquals(2, statusOut.exitCode)
        assertTrue(statusOut.stdout.contains("state=broken"))
        assertTrue(statusOut.stdout.contains("issueCount="))
    }

    @Test
    fun parserErrorsUseExitCodeOneAndUsageOutput() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-empty").toString() },
        )

        val output = run(app, listOf("status", "--json", "extra"))
        assertEquals(1, output.exitCode)
        assertTrue(output.stderr.contains("Error: positional arguments must appear before options"))
        assertTrue(output.stderr.contains("Usage:"))
    }

    @Test
    fun helpCommandPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-help").toString() },
        )

        val output = run(app, listOf("help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub CLI"))
        assertTrue(output.stdout.contains("Lifecycle Commands:"))
        assertTrue(output.stdout.contains("Run 'cli help <command>' for command-specific details."))
    }

    @Test
    fun emptyArgvPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-empty-help").toString() },
        )

        val output = run(app, emptyList())
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub CLI"))
        assertTrue(output.stdout.contains("Resource Commands:"))
    }

    @Test
    fun topLevelHelpFlagPrintsGeneralHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-help-flag").toString() },
        )

        val output = run(app, listOf("--help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("DexClub CLI"))
        assertTrue(output.stdout.contains("Version:"))
        assertTrue(output.stdout.contains("Dex Analysis Commands:"))
    }

    @Test
    fun versionFlagPrintsBuildVersion() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-version").toString() },
        )

        val output = run(app, listOf("--version"))
        assertEquals(0, output.exitCode)
        assertEquals(CliBuildInfo.version, output.stdout.trim())
    }

    @Test
    fun commandHelpFlagPrintsCommandHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-command-help").toString() },
        )

        val output = run(app, listOf("find-method", "--help"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("Command:"))
        assertTrue(output.stdout.contains("find-method"))
        assertTrue(output.stdout.contains("Usage:"))
        assertTrue(output.stdout.contains(CliUsages.findMethod))
    }

    @Test
    fun helpCommandPrintsCommandHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-help-command").toString() },
        )

        val output = run(app, listOf("help", "manifest"))
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("Command:"))
        assertTrue(output.stdout.contains("manifest"))
        assertTrue(output.stdout.contains(CliUsages.manifest))
    }

    @Test
    fun unknownCommandHintsHelp() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-unknown-command").toString() },
        )

        val output = run(app, listOf("wat"))
        assertEquals(1, output.exitCode)
        assertTrue(output.stderr.contains("Error: unknown command: wat"))
        assertTrue(output.stderr.contains("Run 'cli help' to see available commands."))
    }

    @Test
    fun manifestReadsPlainManifestFileThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-manifest-file")
        val manifestFile = workspaceDir.resolve("AndroidManifest.xml")
        val manifestText = """<manifest package="fixture.file" />"""
        manifestFile.writeText(manifestText)
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", manifestFile.toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("manifest"))
        assertEquals(0, output.exitCode, output.stderr)
        assertEquals(manifestText, output.stdout.trim())
    }

    @Test
    fun manifestJsonReadsApkThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-manifest-apk")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileBinaryManifestApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.apk"><application android:label="fixture" /></manifest>""",
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("manifest", "--json"))
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        assertEquals("app.apk", parsed.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("AndroidManifest.xml", parsed.getValue("sourceEntry").jsonPrimitive.content)
        assertTrue(parsed.getValue("text").jsonPrimitive.content.contains("""package="fixture.apk""""))
    }

    @Test
    fun manifestReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-manifest-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("manifest"))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'manifest' is not supported"), output.stderr)
    }

    @Test
    fun resTableReadsApkThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-res-table-apk")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.res"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("res-table", "--json"))
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        assertEquals("app.apk", parsed.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("resources.arsc", parsed.getValue("sourceEntry").jsonPrimitive.content)
        assertEquals(1, parsed.getValue("packageCount").jsonPrimitive.content.toInt())
        assertEquals(1, parsed.getValue("typeCount").jsonPrimitive.content.toInt())
        assertEquals(1, parsed.getValue("entryCount").jsonPrimitive.content.toInt())
        val entry = parsed.getValue("entries").jsonArray.single().jsonObject
        assertEquals("string", entry.getValue("type").jsonPrimitive.content)
        assertEquals("app_name", entry.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun resTableReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-res-table-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("res-table"))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'res-table' is not supported"), output.stderr)
    }

    @Test
    fun decodeXmlReadsApkEntryThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-decode-xml-apk")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.decode"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
            layoutXml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/app_name" />
                </LinearLayout>
            """.trimIndent(),
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf("decode-xml", "--path", "res/layout/activity_main.xml", "--json"),
        )
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        assertEquals("app.apk", parsed.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("res/layout/activity_main.xml", parsed.getValue("sourceEntry").jsonPrimitive.content)
        assertTrue(parsed.getValue("text").jsonPrimitive.content.contains("LinearLayout"))
    }

    @Test
    fun decodeXmlReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-decode-xml-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("decode-xml", "--path", "res/layout/activity_main.xml"))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'decode-xml' is not supported"), output.stderr)
    }

    @Test
    fun listResReadsApkThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-list-res-apk")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.list"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
            layoutXml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            """.trimIndent(),
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("list-res", "--json"))
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        val layoutEntry = parsed.first { element ->
            val obj = element.jsonObject
            obj["type"]?.jsonPrimitive?.content == "layout" &&
                obj["name"]?.jsonPrimitive?.content == "activity_main"
        }.jsonObject
        assertEquals("res/layout/activity_main.xml", layoutEntry.getValue("filePath").jsonPrimitive.content)
        assertEquals("app.apk", layoutEntry.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("table-backed", layoutEntry.getValue("resolution").jsonPrimitive.content)
    }

    @Test
    fun listResReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-list-res-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("list-res"))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'list-res' is not supported"), output.stderr)
    }

    @Test
    fun resolveResReadsResourceValueThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-resolve-res")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.resolve"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf("resolve-res", "--type", "string", "--name", "app_name", "--json"),
        )
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        assertEquals("string", parsed.getValue("type").jsonPrimitive.content)
        assertEquals("app_name", parsed.getValue("name").jsonPrimitive.content)
        assertEquals("DexClub Fixture", parsed.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun resolveResReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-resolve-res-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("resolve-res", "--type", "string", "--name", "app_name"))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'resolve-res' is not supported"), output.stderr)
    }

    @Test
    fun resolveResParserRejectsMutuallyExclusiveSelectors() {
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { createTempDirectory("dexclub-cli-resolve-usage").toString() },
        )

        val output = run(
            app,
            listOf("resolve-res", "--id", "0x7f010001", "--type", "string", "--name", "app_name"),
        )

        assertEquals(1, output.exitCode)
        assertTrue(output.stderr.contains("Error: --id and --type/--name are mutually exclusive"))
        assertTrue(output.stderr.contains("Usage:"))
    }

    @Test
    fun findResRunsThroughCliPipeline() {
        val workspaceDir = createTempDirectory("dexclub-cli-find-res")
        val apkFile = workspaceDir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.findcli"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Login</string>
                    <string name="login_title">Login Title</string>
                    <string name="welcome_message">Welcome</string>
                </resources>
            """.trimIndent(),
        )
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", apkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-res",
                "--query-json",
                """{"type":"string","value":"login","contains":true,"ignoreCase":true}""",
                "--offset",
                "1",
                "--limit",
                "1",
                "--json",
            ),
        )
        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(1, parsed.size)
        val hit = parsed.single().jsonObject
        assertEquals("login_title", hit.getValue("name").jsonPrimitive.content)
        assertEquals("Login Title", hit.getValue("value").jsonPrimitive.content)
        assertEquals("app.apk", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun findResReturnsCapabilityErrorOnDexWorkspace() {
        val workspaceDir = createTempDirectory("dexclub-cli-find-res-unsupported")
        workspaceDir.resolve("classes.dex").writeText("")
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { workspaceDir.toString() },
        )

        val initOut = run(app, listOf("init", workspaceDir.resolve("classes.dex").toString()))
        assertEquals(0, initOut.exitCode)

        val output = run(app, listOf("find-res", "--query-json", """{"type":"string","value":"login"}"""))
        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("command 'find-res' is not supported"), output.stderr)
    }

    @Test
    fun findClassRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val queryFile = File(fixture.dexWorkspaceDir, "find-class.json").apply {
            writeText(
                """{"matcher":{"className":{"value":"SearchTarget","matchType":"Contains","ignoreCase":true}}}""",
                Charsets.UTF_8,
            )
        }
        val output = run(
            app,
            listOf(
                "find-class",
                "--query-file",
                queryFile.absolutePath,
                "--offset",
                "1",
                "--limit",
                "1",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(1, parsed.size)
        val hit = parsed.single().jsonObject
        assertEquals("Lfixture/samples/SampleSearchTarget;", hit.getValue("className").jsonPrimitive.content)
        assertEquals("fixture.dex", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun findMethodRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-method",
                "--query-json",
                """{"matcher":{"name":{"value":"exposeNeedle","matchType":"Equals"}}}""",
                "--offset",
                "1",
                "--limit",
                "1",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(1, parsed.size)
        val hit = parsed.single().jsonObject
        assertEquals("fixture.samples.SampleSearchTarget", hit.getValue("className").jsonPrimitive.content)
        assertEquals("exposeNeedle", hit.getValue("methodName").jsonPrimitive.content)
        assertEquals("fixture.dex", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun findMethodAcceptsShellQuotedQueryJson() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-method",
                "--query-json",
                """'{"matcher":{"name":{"value":"exposeNeedle","matchType":"Equals"}}}'""",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(2, parsed.size)
    }

    @Test
    fun findFieldRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-field",
                "--query-json",
                """{"matcher":{"name":{"value":"NEEDLE","matchType":"Equals"}}}""",
                "--offset",
                "1",
                "--limit",
                "1",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(1, parsed.size)
        val hit = parsed.single().jsonObject
        assertEquals("fixture.samples.SampleSearchTarget", hit.getValue("className").jsonPrimitive.content)
        assertEquals("NEEDLE", hit.getValue("fieldName").jsonPrimitive.content)
        assertEquals("fixture.dex", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun findClassUsingStringsRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-class-using-strings",
                "--query-json",
                """{"groups":{"needle-a":[{"value":"dexclub-needle-string","matchType":"Equals"}],"needle-b":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}""",
                "--offset",
                "1",
                "--limit",
                "1",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        assertEquals(1, parsed.size)
        val hit = parsed.single().jsonObject
        assertEquals("Lfixture/samples/SampleSearchTarget;", hit.getValue("className").jsonPrimitive.content)
        assertEquals("fixture.dex", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun findMethodUsingStringsRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "find-method-using-strings",
                "--query-json",
                """{"groups":{"needle-a":[{"value":"dexclub-needle-string","matchType":"Equals"}],"needle-b":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}""",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonArray
        val hit = parsed.firstOrNull { element ->
            val method = element.jsonObject
            method["className"]?.jsonPrimitive?.content == "fixture.samples.SampleSearchTarget" &&
                method["methodName"]?.jsonPrimitive?.content == "exposeNeedle"
        }?.jsonObject
        assertTrue(hit != null, output.stdout)
        assertEquals("fixture.dex", hit.getValue("sourcePath").jsonPrimitive.content)
    }

    @Test
    fun inspectMethodReturnsRequestedMethodDetailsAsJson() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "inspect-method",
                "--descriptor",
                "Lfixture/samples/SampleSearchTarget;->readMutableNeedle()Ljava/lang/String;",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        val method = parsed.getValue("method").jsonObject
        assertEquals("fixture.samples.SampleSearchTarget", method.getValue("className").jsonPrimitive.content)
        assertEquals("readMutableNeedle", method.getValue("methodName").jsonPrimitive.content)
        assertEquals("fixture.dex", method.getValue("sourcePath").jsonPrimitive.content)

        val usingField = parsed.getValue("usingFields").jsonArray.single().jsonObject
        assertEquals("Read", usingField.getValue("usingType").jsonPrimitive.content)
        val field = usingField.getValue("field").jsonObject
        assertEquals("fixture.samples.SampleSearchTarget", field.getValue("className").jsonPrimitive.content)
        assertEquals("mutableNeedle", field.getValue("fieldName").jsonPrimitive.content)

        val callers = parsed.getValue("callers").jsonArray
        assertEquals(1, callers.size)
        assertEquals(
            "callReadMutableNeedle",
            callers.single().jsonObject.getValue("methodName").jsonPrimitive.content,
        )

        assertEquals(0, parsed.getValue("invokes").jsonArray.size)
    }

    @Test
    fun inspectMethodIncludeOmitsUnrequestedSections() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "inspect-method",
                "--descriptor",
                "Lfixture/samples/SampleSearchTarget;->readMutableNeedle()Ljava/lang/String;",
                "--include",
                "using-fields,callers",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        assertTrue("usingFields" in parsed)
        assertTrue("callers" in parsed)
        assertTrue("invokes" !in parsed)
    }

    @Test
    fun inspectMethodReturnsWorkspaceErrorWhenDescriptorIsAmbiguous() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.ambiguousWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.ambiguousApkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "inspect-method",
                "--descriptor",
                "Lfixture/samples/SampleSearchTarget;->readMutableNeedle()Ljava/lang/String;",
            ),
        )

        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("requires a unique descriptor within the workspace"), output.stderr)
    }

    @Test
    fun inspectMethodFindsCrossDexCallersAndResolvesRealSourceEntries() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.crossDexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.crossDexApkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "inspect-method",
                "--descriptor",
                "Lfixture/samples/SplitTarget;->readFromHelper()Ljava/lang/String;",
                "--json",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val parsed = Json.parseToJsonElement(output.stdout).jsonObject
        val method = parsed.getValue("method").jsonObject
        assertEquals("classes2.dex", method.getValue("sourceEntry").jsonPrimitive.content)

        val callers = parsed.getValue("callers").jsonArray
        assertEquals(1, callers.size)
        val caller = callers.single().jsonObject
        assertEquals("invokeTarget", caller.getValue("methodName").jsonPrimitive.content)
        assertEquals("classes.dex", caller.getValue("sourceEntry").jsonPrimitive.content)

        val usingFields = parsed.getValue("usingFields").jsonArray
        val sharedField = usingFields.first { element ->
            element.jsonObject.getValue("field").jsonObject.getValue("fieldName").jsonPrimitive.content == "sharedField"
        }.jsonObject.getValue("field").jsonObject
        assertEquals("classes.dex", sharedField.getValue("sourceEntry").jsonPrimitive.content)

        val helperInvoke = parsed.getValue("invokes").jsonArray.first { element ->
            val methodObject = element.jsonObject
            methodObject.getValue("className").jsonPrimitive.content == "fixture.samples.SplitHelper" &&
                methodObject.getValue("methodName").jsonPrimitive.content == "helper"
        }.jsonObject
        assertEquals("classes.dex", helperInvoke.getValue("sourceEntry").jsonPrimitive.content)
    }

    @Test
    fun exportClassSmaliRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val outputFile = File(fixture.dexWorkspaceDir, "SampleSearchTarget.smali")
        val output = run(
            app,
            listOf(
                "export-class-smali",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                outputFile.absolutePath,
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals("output=${outputFile.absolutePath}", output.stdout.trimEnd())
        assertTrue(outputFile.isFile)
        assertTrue(outputFile.readText().contains("Lfixture/samples/SampleSearchTarget;"))
    }

    @Test
    fun exportClassSmaliReturnsWorkspaceErrorWhenClassIsAmbiguous() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.ambiguousWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.ambiguousApkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "export-class-smali",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.smali").absolutePath,
            ),
        )

        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("specify --source-path"), output.stderr)
    }

    @Test
    fun exportMethodSmaliRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val outputFile = File(fixture.dexWorkspaceDir, "exposeNeedle.snippet.smali")
        val output = run(
            app,
            listOf(
                "export-method-smali",
                "--method",
                "Lfixture/samples/SampleSearchTarget;->exposeNeedle()Ljava/lang/String;",
                "--output",
                outputFile.absolutePath,
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals("output=${outputFile.absolutePath}", output.stdout.trimEnd())
        assertTrue(outputFile.readText().startsWith(".method public exposeNeedle()Ljava/lang/String;"))
    }

    @Test
    fun exportMethodSmaliClassModeRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val outputFile = File(fixture.dexWorkspaceDir, "exposeNeedle.class.smali")
        val output = run(
            app,
            listOf(
                "export-method-smali",
                "--method",
                "Lfixture/samples/SampleSearchTarget;->exposeNeedle()Ljava/lang/String;",
                "--output",
                outputFile.absolutePath,
                "--mode",
                "class",
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        val text = outputFile.readText()
        assertTrue(text.contains(".class public Lfixture/samples/SampleSearchTarget;"))
        assertTrue(!text.contains(".field public static final NEEDLE:"))
    }

    @Test
    fun exportClassDexRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val outputFile = File(fixture.dexWorkspaceDir, "SampleSearchTarget.dex")
        val output = run(
            app,
            listOf(
                "export-class-dex",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                outputFile.absolutePath,
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals("output=${outputFile.absolutePath}", output.stdout.trimEnd())
        assertTrue(outputFile.isFile)
        assertTrue(isDexFile(outputFile))
    }

    @Test
    fun exportClassDexReturnsWorkspaceErrorWhenClassIsAmbiguous() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.ambiguousWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.ambiguousApkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "export-class-dex",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.dex").absolutePath,
            ),
        )

        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("specify --source-path"), output.stderr)
    }

    @Test
    fun exportClassJavaRunsThroughCliPipeline() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.dexWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.dexFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val outputFile = File(fixture.dexWorkspaceDir, "SampleSearchTarget.java")
        val output = run(
            app,
            listOf(
                "export-class-java",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                outputFile.absolutePath,
            ),
        )

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals("output=${outputFile.absolutePath}", output.stdout.trimEnd())
        val text = outputFile.readText()
        assertTrue(text.contains("class SampleSearchTarget"))
        assertTrue(text.contains("dexclub-needle-string"))
    }

    @Test
    fun exportClassJavaReturnsWorkspaceErrorWhenClassIsAmbiguous() {
        val fixture = CliDexFixture.generated()
        val app = CliApp(
            services = createDefaultServices(),
            cwdProvider = { fixture.ambiguousWorkspaceDir.absolutePath },
        )

        val initOut = run(app, listOf("init", fixture.ambiguousApkFile.absolutePath))
        assertEquals(0, initOut.exitCode)

        val output = run(
            app,
            listOf(
                "export-class-java",
                "--class",
                "fixture.samples.SampleSearchTarget",
                "--output",
                File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.java").absolutePath,
            ),
        )

        assertEquals(2, output.exitCode)
        assertTrue(output.stderr.contains("specify --source-path"), output.stderr)
    }

    private fun run(app: CliApp, argv: List<String>): CapturedOutput {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val exitCode = app.run(argv, stdout, stderr)
        return CapturedOutput(
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            exitCode = exitCode,
        )
    }

    private data class CapturedOutput(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )

    private fun createZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun compileBinaryManifestApk(outputApk: File, manifestText: String) {
        val root = outputApk.parentFile
        val manifestFile = File(root, "AndroidManifest.xml").apply {
            writeText(manifestText, Charsets.UTF_8)
        }
        runCommand(
            command = listOf(
                resolveAapt2Command(),
                "link",
                "-o",
                outputApk.absolutePath,
                "--manifest",
                manifestFile.absolutePath,
                "-I",
                resolveAndroidJar().absolutePath,
            ),
            workingDirectory = root,
        )
    }

    private fun compileResourceApk(
        outputApk: File,
        manifestText: String,
        resourceXml: String,
        layoutXml: String? = null,
    ) {
        val root = outputApk.parentFile
        val manifestFile = File(root, "AndroidManifest.xml").apply {
            writeText(manifestText, Charsets.UTF_8)
        }
        val valuesDir = File(root, "res/values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(resourceXml, Charsets.UTF_8)
        if (layoutXml != null) {
            val layoutDir = File(root, "res/layout").apply { mkdirs() }
            File(layoutDir, "activity_main.xml").writeText(layoutXml, Charsets.UTF_8)
        }
        val compiledDir = File(root, "compiled-res").apply { mkdirs() }
        runCommand(
            command = listOf(
                resolveAapt2Command(),
                "compile",
                "--dir",
                valuesDir.parentFile.absolutePath,
                "-o",
                compiledDir.absolutePath,
            ),
            workingDirectory = root,
        )
        val compiledRes = compiledDir.listFiles()
            ?.filter(File::isFile)
            ?.sortedBy(File::getName)
            ?.takeIf { it.isNotEmpty() }
            ?: error("未生成编译资源产物")
        runCommand(
            command = buildList {
                add(resolveAapt2Command())
                add("link")
                add("-o")
                add(outputApk.absolutePath)
                add("--manifest")
                add(manifestFile.absolutePath)
                add("-I")
                add(resolveAndroidJar().absolutePath)
                add("--auto-add-overlay")
                compiledRes.forEach { compiled ->
                    add("-R")
                    add(compiled.absolutePath)
                }
            },
            workingDirectory = root,
        )
    }

    private fun resolveAapt2Command(): String {
        val sdkRoot = System.getenv("ANDROID_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: error("未找到 ANDROID_HOME，无法定位 aapt2")
        return sdkRoot.resolve("build-tools").listFiles()
            ?.sortedByDescending(File::getName)
            ?.asSequence()
            ?.map { it.resolve("aapt2.exe") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath
            ?: error("未找到可用的 aapt2.exe")
    }

    private fun resolveAndroidJar(): File {
        val sdkRoot = System.getenv("ANDROID_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: error("未找到 ANDROID_HOME，无法定位 android.jar")
        return sdkRoot.resolve("platforms").walkTopDown()
            .filter { it.isFile && it.name == "android.jar" }
            .sortedByDescending(File::getAbsolutePath)
            .firstOrNull()
            ?: error("未找到可用的 android.jar")
    }

    private fun runCommand(command: List<String>, workingDirectory: File) {
        val process = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) {
            buildString {
                appendLine("命令执行失败: ${command.joinToString(" ")}")
                append(output)
            }
        }
    }
}

private fun isDexFile(file: File): Boolean {
    val header = ByteArray(8)
    file.inputStream().use { input ->
        val read = input.read(header)
        if (read < header.size) return false
    }
    return header.decodeToString() == "dex\n035\u0000"
}

private class CliDexFixture(
    val dexWorkspaceDir: File,
    val dexFile: File,
    val ambiguousWorkspaceDir: File,
    val ambiguousApkFile: File,
    val crossDexWorkspaceDir: File,
    val crossDexApkFile: File,
) {
    companion object {
        fun generated(): CliDexFixture {
            val root = createTempDirectory("dexclub-cli-dex-fixture").toFile()
            val sampleClasses = compileJava(
                root = root,
                fileName = "SampleSearchTarget.java",
                source = """
                    package fixture.samples;
                    public class SampleSearchTarget {
                        public static final String NEEDLE = "dexclub-needle-string";
                        public String mutableNeedle = NEEDLE;
                        public String exposeNeedle() {
                            return NEEDLE;
                        }
                        public String callExposeNeedle() {
                            return exposeNeedle();
                        }
                        public String readMutableNeedle() {
                            return mutableNeedle;
                        }
                        public String callReadMutableNeedle() {
                            return readMutableNeedle();
                        }
                    }
                """.trimIndent(),
            )
            val anotherClasses = compileJava(
                root = root,
                fileName = "AnotherSearchTarget.java",
                source = """
                    package fixture.samples;
                    public class AnotherSearchTarget {
                        public static final String NEEDLE = "dexclub-needle-string";
                        public String exposeNeedle() {
                            return NEEDLE;
                        }
                    }
                """.trimIndent(),
            )

            val sampleDex = compileDex(root, "sample", sampleClasses)
            val duplicateSampleDex = compileDex(root, "duplicate", sampleClasses)
            val dexWorkspaceDir = File(root, "dex-input").also(File::mkdirs)
            val dexFile = compileDex(root, "fixture", sampleClasses, anotherClasses)
                .copyTo(File(dexWorkspaceDir, "fixture.dex"), overwrite = true)

            val ambiguousWorkspaceDir = File(root, "ambiguous-dex-input").also(File::mkdirs)
            val ambiguousApkFile = File(ambiguousWorkspaceDir, "fixture.apk")
            createPseudoApk(
                outputApk = ambiguousApkFile,
                "classes.dex" to sampleDex,
                "classes2.dex" to duplicateSampleDex,
            )

            val splitHelperClasses = compileJava(
                root = root,
                fileName = "SplitHelper.java",
                source = """
                    package fixture.samples;
                    public class SplitHelper {
                        public String sharedField = "split-field";
                        public String helper() {
                            return "split-helper";
                        }
                    }
                """.trimIndent(),
            )
            val splitTargetClasses = compileJava(
                root = root,
                fileName = "SplitTarget.java",
                classpath = listOf(splitHelperClasses),
                source = """
                    package fixture.samples;
                    public class SplitTarget {
                        private final SplitHelper helper = new SplitHelper();
                        public String readFromHelper() {
                            return helper.sharedField + helper.helper();
                        }
                    }
                """.trimIndent(),
            )
            val splitCallerClasses = compileJava(
                root = root,
                fileName = "SplitCaller.java",
                classpath = listOf(splitHelperClasses, splitTargetClasses),
                source = """
                    package fixture.samples;
                    public class SplitCaller {
                        public String invokeTarget() {
                            return new SplitTarget().readFromHelper();
                        }
                    }
                """.trimIndent(),
            )
            val splitClassesDex = compileDex(root, "split-classes", splitHelperClasses, splitCallerClasses)
            val splitClasses2Dex = compileDex(root, "split-classes2", splitTargetClasses)
            val crossDexWorkspaceDir = File(root, "cross-dex-input").also(File::mkdirs)
            val crossDexApkFile = File(crossDexWorkspaceDir, "fixture.apk")
            createPseudoApk(
                outputApk = crossDexApkFile,
                "classes.dex" to splitClassesDex,
                "classes2.dex" to splitClasses2Dex,
            )

            return CliDexFixture(
                dexWorkspaceDir = dexWorkspaceDir,
                dexFile = dexFile,
                ambiguousWorkspaceDir = ambiguousWorkspaceDir,
                ambiguousApkFile = ambiguousApkFile,
                crossDexWorkspaceDir = crossDexWorkspaceDir,
                crossDexApkFile = crossDexApkFile,
            )
        }

        private fun compileJava(
            root: File,
            fileName: String,
            source: String,
            classpath: List<File> = emptyList(),
        ): File {
            val sourceDir = File(root, "src-$fileName/fixture/samples").also(File::mkdirs)
            val sourceFile = File(sourceDir, fileName).apply {
                writeText(source, Charsets.UTF_8)
            }
            val classesDir = File(root, "classes-$fileName").also(File::mkdirs)
            runCommand(
                command = buildList {
                    add("javac")
                    add("--release")
                    add("8")
                    if (classpath.isNotEmpty()) {
                        add("-classpath")
                        add(classpath.joinToString(File.pathSeparator) { it.absolutePath })
                    }
                    add("-d")
                    add(classesDir.absolutePath)
                    add(sourceFile.absolutePath)
                },
                workingDirectory = root,
            )
            return classesDir
        }

        private fun compileDex(root: File, name: String, vararg classesDirs: File): File {
            val dexDir = File(root, "dex-$name").also(File::mkdirs)
            val classFiles = classesDirs.asSequence()
                .flatMap { classesDir ->
                    classesDir.walkTopDown()
                        .asSequence()
                        .filter { it.isFile && it.extension == "class" }
                        .map { it.absolutePath }
                }
                .toList()
            runCommand(
                command = buildList {
                    add(resolveD8Command())
                    add("--min-api")
                    add("21")
                    add("--output")
                    add(dexDir.absolutePath)
                    addAll(classFiles)
                },
                workingDirectory = root,
            )
            return File(dexDir, "classes.dex")
        }

        private fun createPseudoApk(outputApk: File, vararg entries: Pair<String, File>) {
            ZipOutputStream(outputApk.outputStream().buffered()).use { zip ->
                entries.forEach { (name, sourceDex) ->
                    zip.putNextEntry(ZipEntry(name))
                    sourceDex.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        private fun resolveD8Command(): String {
            val envRoots = listOfNotNull(
                System.getenv("ANDROID_SDK_ROOT"),
                System.getenv("ANDROID_HOME"),
            ).map(::File)
            val candidates = buildList {
                envRoots.forEach { root ->
                    root.resolve("build-tools").listFiles()
                        ?.sortedByDescending(File::getName)
                        ?.forEach { buildToolsDir ->
                            add(buildToolsDir.resolve("d8").path)
                            add(buildToolsDir.resolve("d8.bat").path)
                        }
                }
                add("d8")
                add("d8.bat")
            }
            return candidates.firstOrNull { candidate ->
                runCatching {
                    val process = ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor() == 0
                }.getOrDefault(false)
            } ?: error("未找到可用的 d8 命令")
        }

        private fun runCommand(command: List<String>, workingDirectory: File) {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) {
                buildString {
                    appendLine("命令执行失败: ${command.joinToString(" ")}")
                    append(output)
                }
            }
        }
    }
}

