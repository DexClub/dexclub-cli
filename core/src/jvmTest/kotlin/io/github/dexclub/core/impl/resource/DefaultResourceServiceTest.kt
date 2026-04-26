package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.shared.CapabilityError
import io.github.dexclub.core.api.shared.Operation
import io.github.dexclub.core.api.shared.createDefaultServices
import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.resource.ResourceDecodeErrorReason
import io.github.dexclub.core.api.resource.ResourceResolution
import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.FindResourcesRequest
import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.shared.PageWindow
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultResourceServiceTest {
    @Test
    fun decodeManifestFromPlainManifestFile() {
        val workdir = createTempDirectory("dexclub-resource-manifest-file")
        val manifestFile = workdir.resolve("AndroidManifest.xml")
        val manifestText = """<manifest package="fixture.file" />"""
        manifestFile.writeText(manifestText)

        val services = createDefaultServices()
        services.workspace.initialize(manifestFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.decodeManifest(workspace)

        assertEquals("AndroidManifest.xml", result.sourcePath)
        assertEquals(null, result.sourceEntry)
        assertEquals(manifestText, result.text)
    }

    @Test
    fun decodeManifestFromBinaryManifestFile() {
        val workdir = createTempDirectory("dexclub-resource-binary-manifest-file")
        val apkFile = workdir.resolve("fixture.apk").toFile()
        compileBinaryManifestApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.binary"><application android:label="fixture" /></manifest>""",
        )
        val manifestFile = workdir.resolve("AndroidManifest.xml")
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: error("APK 中缺少 AndroidManifest.xml")
            manifestFile.writeBytes(zip.getInputStream(entry).use { it.readBytes() })
        }

        val services = createDefaultServices()
        services.workspace.initialize(manifestFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.decodeManifest(workspace)

        assertEquals("AndroidManifest.xml", result.sourcePath)
        assertEquals(null, result.sourceEntry)
        assertTrue(result.text.contains("""package="fixture.binary""""))
    }

    @Test
    fun decodeManifestFromApkWorkspace() {
        val workdir = createTempDirectory("dexclub-resource-apk")
        val apkFile = workdir.resolve("app.apk")
        val manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.apk"><application android:label="fixture" /></manifest>"""
        compileBinaryManifestApk(apkFile.toFile(), manifestText)

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.decodeManifest(workspace)

        assertEquals("app.apk", result.sourcePath)
        assertEquals("AndroidManifest.xml", result.sourceEntry)
        assertTrue(result.text.contains("""package="fixture.apk""""))
        assertTrue(result.text.contains("<application"))
    }

    @Test
    fun decodeManifestCreatesAndRebuildsCache() {
        val workdir = createTempDirectory("dexclub-resource-manifest-cache")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileBinaryManifestApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.manifestcache"><application android:label="fixture" /></manifest>""",
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        val cacheFile = workdir.resolve(".dexclub/targets/${workspace.activeTargetId}/cache/decoded/manifest.json").toFile()
        assertTrue(!cacheFile.exists())

        services.resource.decodeManifest(workspace)

        assertTrue(cacheFile.isFile)
        val first = Json.parseToJsonElement(cacheFile.readText()).jsonObject
        assertEquals("app.apk", first.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("AndroidManifest.xml", first.getValue("sourceEntry").jsonPrimitive.content)
        assertTrue(first.getValue("toolVersion").jsonPrimitive.content.isNotBlank())

        cacheFile.writeText(
            """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-04-25T12:20:00Z",
                  "targetId": "${workspace.activeTargetId}",
                  "toolVersion": "stale-tool",
                  "sourcePath": "app.apk",
                  "sourceEntry": "AndroidManifest.xml",
                  "sourceFingerprint": "stale-fingerprint",
                  "format": "xml-text",
                  "text": "<manifest package=\"stale\" />"
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val rebuilt = services.resource.decodeManifest(workspace)
        assertTrue(rebuilt.text.contains("""package="fixture.manifestcache""""))
        val refreshed = Json.parseToJsonElement(cacheFile.readText()).jsonObject
        assertTrue(refreshed.getValue("toolVersion").jsonPrimitive.content != "stale-tool")
        assertTrue(refreshed.getValue("sourceFingerprint").jsonPrimitive.content != "stale-fingerprint")
    }

    @Test
    fun decodeManifestRequiresManifestCapability() {
        val workdir = createTempDirectory("dexclub-resource-no-manifest")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.decodeManifest(workspace)
        }

        assertEquals(Operation.ManifestDecode, error.operation)
        assertTrue(error.requiredCapability == "manifestDecode")
    }

    @Test
    fun decodeManifestFailsWhenApkHasNoManifestEntry() {
        val workdir = createTempDirectory("dexclub-resource-manifest-missing")
        val apkFile = workdir.resolve("broken.apk")
        apkFile.writeBytes(createZip("classes.dex" to byteArrayOf(0x64, 0x65, 0x78)))

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<ResourceDecodeError> {
            services.resource.decodeManifest(workspace)
        }

        assertEquals(ResourceDecodeErrorReason.ManifestEntryMissing, error.reason)
    }

    @Test
    fun dumpResourceTableFromStandaloneArscFile() {
        val workdir = createTempDirectory("dexclub-resource-arsc-file")
        val apkFile = workdir.resolve("fixture.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.arsc"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )
        val arscFile = workdir.resolve("resources.arsc")
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("resources.arsc") ?: error("APK 中缺少 resources.arsc")
            arscFile.writeBytes(zip.getInputStream(entry).use { it.readBytes() })
        }

        val services = createDefaultServices()
        services.workspace.initialize(arscFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.dumpResourceTable(workspace)

        assertEquals("resources.arsc", result.sourcePath)
        assertEquals(null, result.sourceEntry)
        assertEquals(1, result.packageCount)
        assertEquals(1, result.typeCount)
        assertEquals(1, result.entryCount)
        val entry = result.entries.single()
        assertEquals("string", entry.type)
        assertEquals("app_name", entry.name)
        assertEquals(ResourceResolution.Unresolved, entry.resolution)
        assertTrue(entry.resourceId?.startsWith("0x") == true)
    }

    @Test
    fun dumpResourceTableFromApkWorkspace() {
        val workdir = createTempDirectory("dexclub-resource-arsc-apk")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.apkarsc"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.dumpResourceTable(workspace)

        assertEquals("app.apk", result.sourcePath)
        assertEquals("resources.arsc", result.sourceEntry)
        assertEquals(1, result.packageCount)
        assertEquals(1, result.typeCount)
        assertEquals(1, result.entryCount)
        assertEquals("app_name", result.entries.single().name)
    }

    @Test
    fun dumpResourceTableCreatesAndRebuildsCache() {
        val workdir = createTempDirectory("dexclub-resource-arsc-cache")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.tablecache"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        val targetId = workspace.activeTargetId
        val cacheFile = workdir.resolve(".dexclub/targets/$targetId/cache/decoded/resource-table.json").toFile()
        assertTrue(!cacheFile.exists())

        services.resource.dumpResourceTable(workspace)

        assertTrue(cacheFile.isFile)
        val first = Json.parseToJsonElement(cacheFile.readText()).jsonObject
        assertEquals("app.apk", first.getValue("sourcePath").jsonPrimitive.content)
        assertEquals("resources.arsc", first.getValue("sourceEntry").jsonPrimitive.content)

        cacheFile.writeText(
            """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-04-25T12:21:00Z",
                  "targetId": "$targetId",
                  "toolVersion": "test",
                  "sourcePath": "app.apk",
                  "sourceEntry": "resources.arsc",
                  "sourceFingerprint": "stale-fingerprint",
                  "format": "resource-table-v1",
                  "payload": {
                    "packages": [],
                    "typeCount": 0,
                    "entries": []
                  }
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val rebuilt = services.resource.dumpResourceTable(workspace)
        assertEquals(1, rebuilt.entryCount)
        val refreshed = Json.parseToJsonElement(cacheFile.readText()).jsonObject
        assertTrue(refreshed.getValue("sourceFingerprint").jsonPrimitive.content != "stale-fingerprint")
        assertEquals(1, refreshed.getValue("payload").jsonObject.getValue("entries").jsonArray.size)
    }

    @Test
    fun dumpResourceTableRequiresResourceTableCapability() {
        val workdir = createTempDirectory("dexclub-resource-no-table")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.dumpResourceTable(workspace)
        }

        assertEquals(Operation.ResourceTableDecode, error.operation)
        assertTrue(error.requiredCapability == "resourceTableDecode")
    }

    @Test
    fun dumpResourceTableFailsWhenApkHasNoResourceTable() {
        val workdir = createTempDirectory("dexclub-resource-arsc-missing")
        val apkFile = workdir.resolve("broken.apk")
        apkFile.writeBytes(createZip("AndroidManifest.xml" to """<manifest package="broken" />""".toByteArray()))

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<ResourceDecodeError> {
            services.resource.dumpResourceTable(workspace)
        }

        assertEquals(ResourceDecodeErrorReason.ResourceTableEntryMissing, error.reason)
    }

    @Test
    fun decodeXmlFromPlainResFile() {
        val workdir = createTempDirectory("dexclub-resource-xml-text")
        val xmlFile = workdir.resolve("activity_main.xml")
        val xmlText = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical" />
        """.trimIndent()
        xmlFile.writeText(xmlText)

        val services = createDefaultServices()
        services.workspace.initialize(xmlFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.decodeXml(
            workspace,
            DecodeXmlRequest(path = "activity_main.xml"),
        )

        assertEquals("activity_main.xml", result.sourcePath)
        assertEquals(null, result.sourceEntry)
        assertEquals(xmlText, result.text)
    }

    @Test
    fun decodeXmlFromApkWorkspace() {
        val workdir = createTempDirectory("dexclub-resource-xml-apk")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.xmlapk"><application android:label="@string/app_name" /></manifest>""",
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

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.decodeXml(
            workspace,
            DecodeXmlRequest(path = "res/layout/activity_main.xml"),
        )

        assertEquals("app.apk", result.sourcePath)
        assertEquals("res/layout/activity_main.xml", result.sourceEntry)
        assertTrue(result.text.contains("<LinearLayout"))
        assertTrue(result.text.contains("TextView"))
    }

    @Test
    fun decodeXmlCreatesAndRebuildsCache() {
        val workdir = createTempDirectory("dexclub-resource-xml-cache")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.xmlcache"><application android:label="@string/app_name" /></manifest>""",
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

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        val targetId = workspace.activeTargetId
        val xmlId = resourceXmlCacheId("app.apk", "res/layout/activity_main.xml")
        val cacheFile = workdir.resolve(".dexclub/targets/$targetId/cache/decoded/xml/$xmlId.json").toFile()
        assertTrue(!cacheFile.exists())

        services.resource.decodeXml(workspace, DecodeXmlRequest(path = "res/layout/activity_main.xml"))

        assertTrue(cacheFile.isFile)
        cacheFile.writeText(
            """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-04-25T12:22:00Z",
                  "targetId": "$targetId",
                  "sourcePath": "app.apk",
                  "sourceEntry": "res/layout/activity_main.xml",
                  "sourceFingerprint": "stale-fingerprint",
                  "format": "xml-text",
                  "text": "<Broken />"
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val rebuilt = services.resource.decodeXml(workspace, DecodeXmlRequest(path = "res/layout/activity_main.xml"))
        assertTrue(rebuilt.text.contains("<LinearLayout"))
        val refreshed = Json.parseToJsonElement(cacheFile.readText()).jsonObject
        assertTrue(refreshed.getValue("sourceFingerprint").jsonPrimitive.content != "stale-fingerprint")
        assertTrue(refreshed.getValue("text").jsonPrimitive.content.contains("<LinearLayout"))
    }

    @Test
    fun decodeXmlRequiresXmlCapability() {
        val workdir = createTempDirectory("dexclub-resource-no-xml")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.decodeXml(workspace, DecodeXmlRequest(path = "res/layout/activity_main.xml"))
        }

        assertEquals(Operation.XmlDecode, error.operation)
        assertTrue(error.requiredCapability == "xmlDecode")
    }

    @Test
    fun decodeXmlFailsWhenPathIsMissing() {
        val workdir = createTempDirectory("dexclub-resource-xml-missing")
        val xmlFile = workdir.resolve("activity_main.xml")
        xmlFile.writeText("<LinearLayout />")

        val services = createDefaultServices()
        services.workspace.initialize(xmlFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<ResourceDecodeError> {
            services.resource.decodeXml(workspace, DecodeXmlRequest(path = "missing.xml"))
        }

        assertEquals(ResourceDecodeErrorReason.XmlPathNotFound, error.reason)
    }

    @Test
    fun listResourceEntriesFromApkWorkspace() {
        val workdir = createTempDirectory("dexclub-resource-list-apk")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.listapk"><application android:label="@string/app_name" /></manifest>""",
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

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val entries = services.resource.listResourceEntries(workspace)

        val layoutEntry = entries.first { it.type == "layout" && it.name == "activity_main" }
        assertEquals("res/layout/activity_main.xml", layoutEntry.filePath)
        assertEquals("app.apk", layoutEntry.sourcePath)
        assertEquals("res/layout/activity_main.xml", layoutEntry.sourceEntry)
        assertEquals(ResourceResolution.TableBacked, layoutEntry.resolution)

        val stringEntry = entries.first { it.type == "string" && it.name == "app_name" }
        assertEquals("app.apk", stringEntry.sourcePath)
        assertEquals(null, stringEntry.filePath)
        assertEquals(ResourceResolution.Unresolved, stringEntry.resolution)
        assertTrue(stringEntry.resourceId?.startsWith("0x") == true)
    }

    @Test
    fun listResourceEntriesAreUnsupportedForStandaloneXmlWorkspace() {
        val workdir = createTempDirectory("dexclub-resource-list-xml")
        val xmlFile = workdir.resolve("activity_main.xml")
        xmlFile.writeText("<LinearLayout />")

        val services = createDefaultServices()
        services.workspace.initialize(xmlFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.listResourceEntries(workspace)
        }
        assertEquals(Operation.ResourceEntryList, error.operation)
    }

    @Test
    fun listResourceEntriesCreatesAndRebuildsIndex() {
        val workdir = createTempDirectory("dexclub-resource-list-index")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.listindex"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
            layoutXml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" />""",
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        val targetId = workspace.activeTargetId
        val indexFile = workdir.resolve(".dexclub/targets/$targetId/cache/indexes/resource-entry-index.json").toFile()
        assertTrue(!indexFile.exists())

        val entries = services.resource.listResourceEntries(workspace)
        assertTrue(indexFile.isFile)
        assertTrue(entries.isNotEmpty())

        indexFile.writeText(
            """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-04-25T12:27:00Z",
                  "targetId": "$targetId",
                  "toolVersion": "test",
                  "contentFingerprint": "stale-fingerprint",
                  "format": "resource-entry-index-v1",
                  "entries": []
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val rebuilt = services.resource.listResourceEntries(workspace)
        assertTrue(rebuilt.isNotEmpty())
        val refreshed = Json.parseToJsonElement(indexFile.readText()).jsonObject
        assertEquals(workspace.snapshot.contentFingerprint, refreshed.getValue("contentFingerprint").jsonPrimitive.content)
        assertTrue(refreshed.getValue("entries").jsonArray.isNotEmpty())
    }

    @Test
    fun listResourceEntriesRequiresResourceEntryCapability() {
        val workdir = createTempDirectory("dexclub-resource-list-no-entry")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.listResourceEntries(workspace)
        }

        assertEquals(Operation.ResourceEntryList, error.operation)
        assertTrue(error.requiredCapability == "resourceEntryList")
    }

    @Test
    fun resolveResourceValueById() {
        val workdir = createTempDirectory("dexclub-resource-resolve-id")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.resolveid"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        val resourceId = services.resource.dumpResourceTable(workspace).entries
            .first { it.type == "string" && it.name == "app_name" }
            .resourceId
            ?: error("缺少资源 id")

        val result = services.resource.resolveResourceValue(
            workspace,
            ResolveResourceRequest(resourceId = resourceId),
        )

        assertEquals(resourceId, result.resourceId)
        assertEquals("string", result.type)
        assertEquals("app_name", result.name)
        assertEquals("DexClub Fixture", result.value)
    }

    @Test
    fun resolveResourceValueByTypeAndName() {
        val workdir = createTempDirectory("dexclub-resource-resolve-name")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.resolvename"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val result = services.resource.resolveResourceValue(
            workspace,
            ResolveResourceRequest(
                type = "string",
                name = "app_name",
            ),
        )

        assertEquals("string", result.type)
        assertEquals("app_name", result.name)
        assertEquals("DexClub Fixture", result.value)
    }

    @Test
    fun resolveResourceValueReusesCachedResourceTablePayload() {
        val workdir = createTempDirectory("dexclub-resource-resolve-cache")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.resolvecache"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Fixture</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        services.resource.dumpResourceTable(workspace)
        val cacheFile = workdir.resolve(".dexclub/targets/${workspace.activeTargetId}/cache/decoded/resource-table.json").toFile()
        cacheFile.writeText(
            cacheFile.readText(Charsets.UTF_8).replace("DexClub Fixture", "Cached Override"),
            Charsets.UTF_8,
        )

        val result = services.resource.resolveResourceValue(
            workspace,
            ResolveResourceRequest(type = "string", name = "app_name"),
        )

        assertEquals("Cached Override", result.value)
    }

    @Test
    fun resolveResourceValueRequiresResourceTableCapability() {
        val workdir = createTempDirectory("dexclub-resource-resolve-no-table")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.resolveResourceValue(
                workspace,
                ResolveResourceRequest(type = "string", name = "app_name"),
            )
        }

        assertEquals(Operation.ResourceTableDecode, error.operation)
        assertTrue(error.requiredCapability == "resourceTableDecode")
    }

    @Test
    fun findResourceEntriesAppliesStableSortAndWindow() {
        val workdir = createTempDirectory("dexclub-resource-find")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.find"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Login</string>
                    <string name="login_title">Login Title</string>
                    <string name="welcome_message">Welcome</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val hits = services.resource.findResourceEntries(
            workspace,
            FindResourcesRequest(
                queryText = """{"type":"string","value":"login","contains":true,"ignoreCase":true}""",
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        val hit = hits.single()
        assertEquals("login_title", hit.name)
        assertEquals("Login Title", hit.value)
        assertEquals("app.apk", hit.sourcePath)
        assertEquals("resources.arsc", hit.sourceEntry)
    }

    @Test
    fun findResourceEntriesReusesCachedResourceTablePayload() {
        val workdir = createTempDirectory("dexclub-resource-find-cache")
        val apkFile = workdir.resolve("app.apk").toFile()
        compileResourceApk(
            outputApk = apkFile,
            manifestText = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="fixture.findcache"><application android:label="@string/app_name" /></manifest>""",
            resourceXml = """
                <resources>
                    <string name="app_name">DexClub Login</string>
                    <string name="login_title">Login Title</string>
                </resources>
            """.trimIndent(),
        )

        val services = createDefaultServices()
        services.workspace.initialize(apkFile.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))
        services.resource.dumpResourceTable(workspace)
        val cacheFile = workdir.resolve(".dexclub/targets/${workspace.activeTargetId}/cache/decoded/resource-table.json").toFile()
        cacheFile.writeText(
            cacheFile.readText(Charsets.UTF_8).replace("Login Title", "Cache Only Match"),
            Charsets.UTF_8,
        )

        val hits = services.resource.findResourceEntries(
            workspace,
            FindResourcesRequest(queryText = """{"type":"string","value":"cache only","contains":true,"ignoreCase":true}"""),
        )

        assertEquals(1, hits.size)
        assertEquals("login_title", hits.single().name)
        assertEquals("Cache Only Match", hits.single().value)
    }

    @Test
    fun findResourceEntriesRequiresResourceTableCapability() {
        val workdir = createTempDirectory("dexclub-resource-find-no-table")
        workdir.resolve("classes.dex").writeText("")

        val services = createDefaultServices()
        services.workspace.initialize(workdir.resolve("classes.dex").toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.toString()))

        val error = assertFailsWith<CapabilityError> {
            services.resource.findResourceEntries(
                workspace,
                FindResourcesRequest(queryText = """{"type":"string","value":"login"}"""),
            )
        }

        assertEquals(Operation.ResourceTableDecode, error.operation)
        assertTrue(error.requiredCapability == "resourceTableDecode")
    }

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
