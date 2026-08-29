import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.application.tasks.CreateStartScripts
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

val dexkitNativeLibraryDirProperty = "dexclub.dexkit.native.library.dir"
val mcpRuntimeFilesDirProperty = "dexclub.mcp.runtime.files.dir"
val externalDexKitNativeDir = providers.gradleProperty("dexkit.native.dir")

kotlin {
    jvmToolchain(21)
}

val dexkitBindingModule = "io.github.dexclub:dexkit-binding:0.0.0-local"

application {
    applicationName = "mcp"
    mainClass = "io.github.dexclub.mcp.MainKt"
    applicationDefaultJvmArgs = listOf(
        "-XX:ErrorFile=hs_err_pid%p.log",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=.",
    )
}

fun patchGeneratedScript(file: File, expected: String, replacement: String) {
    val original = file.readText()
    check(original.contains(expected)) {
        "Could not find the expected fragment in generated script ${file.absolutePath}; the startScripts template may have changed"
    }
    file.writeText(original.replace(expected, replacement))
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        patchGeneratedScript(
            file = windowsScript,
            expected = """set DEFAULT_JVM_OPTS="-XX:ErrorFile=hs_err_pid%%p.log" "-XX:+HeapDumpOnOutOfMemoryError" "-XX:HeapDumpPath=."""",
            replacement =
                """set DEFAULT_JVM_OPTS="-D$mcpRuntimeFilesDirProperty=%APP_HOME%\bin" "-XX:ErrorFile=%APP_HOME%\bin\hs_err_pid%%p.log" "-XX:+HeapDumpOnOutOfMemoryError" "-XX:HeapDumpPath=%APP_HOME%\bin"""",
        )
        patchGeneratedScript(
            file = unixScript,
            expected = """DEFAULT_JVM_OPTS='"-XX:ErrorFile=hs_err_pid%p.log" "-XX:+HeapDumpOnOutOfMemoryError" "-XX:HeapDumpPath=."'""",
            replacement =
                $$"""DEFAULT_JVM_OPTS="\"-D$$mcpRuntimeFilesDirProperty=$APP_HOME/bin\" \"-XX:ErrorFile=$APP_HOME/bin/hs_err_pid%p.log\" \"-XX:+HeapDumpOnOutOfMemoryError\" \"-XX:HeapDumpPath=$APP_HOME/bin\""""",
        )
    }
}

fun resolveMcpVersion(): String {
    val configured = project.version.toString()
    if (configured.isNotBlank() && configured != "unspecified") {
        return configured
    }

    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--always", "--dirty")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.waitFor(10, TimeUnit.SECONDS)
        process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .trim()
            .ifBlank { "dev" }
    } catch (_: Exception) {
        "dev"
    }
}

dependencies {
    implementation(project(":app-service"))
    implementation(platform(libs.ktor.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mcp.kotlin.sdk.server)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.ktor.serialization.kotlinx.json)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(project(":app-service"))
    testImplementation(dexkitBindingModule)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

val sourceSets = the<SourceSetContainer>()
val mcpTestSourceSet = sourceSets["test"]
val mcpSmokeTestSourceSet = sourceSets.create("testSmoke") {
    compileClasspath += sourceSets["main"].output + mcpTestSourceSet.output
    runtimeClasspath += output + compileClasspath + mcpTestSourceSet.runtimeClasspath
}

configurations[mcpSmokeTestSourceSet.implementationConfigurationName].extendsFrom(configurations["testImplementation"])
configurations[mcpSmokeTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])

fun Test.configureMcpTestRuntime() {
    useJUnitPlatform()
    systemProperty("dexclub.repo.root", rootDir.absolutePath)
    val externalNativeDir = externalDexKitNativeDir.orNull?.trim().orEmpty()
    if (externalNativeDir.isNotEmpty()) {
        systemProperty(dexkitNativeLibraryDirProperty, externalNativeDir)
    }
}

fun registerMcpTestTask(
    name: String,
    description: String,
    vararg classNames: String,
) = tasks.register<Test>(name) {
    group = "verification"
    this.description = description
    testClassesDirs = mcpTestSourceSet.output.classesDirs
    classpath = mcpTestSourceSet.runtimeClasspath
    configureMcpTestRuntime()
    filter {
        classNames.forEach(::includeTestsMatching)
    }
}

tasks.test {
    configureMcpTestRuntime()
}

val testSession by registerMcpTestTask(
    name = "testSession",
    description = "Run MCP session lifecycle, startup configuration, and session storage tests",
    "io.github.dexclub.mcp.McpMainTest",
    "io.github.dexclub.mcp.McpDexContextRegistryTest",
    "io.github.dexclub.mcp.McpExecutionSupportTest",
    "io.github.dexclub.mcp.McpSessionToolsTest",
)

val testDex by registerMcpTestTask(
    name = "testDex",
    description = "Run MCP dex query, inspect, and export tests",
    "io.github.dexclub.mcp.McpDexToolsTest",
    "io.github.dexclub.mcp.McpFindQuerySchemaTest",
    "io.github.dexclub.mcp.McpSkillContractTest",
)

val testResource by registerMcpTestTask(
    name = "testResource",
    description = "Run MCP manifest, resource, and XML tests",
    "io.github.dexclub.mcp.McpResourceToolsTest",
)

val testModels by registerMcpTestTask(
    name = "testModels",
    description = "Run MCP result mapping, field projection, and error rendering tests",
    "io.github.dexclub.mcp.McpModelsTest",
)

val testSmoke = tasks.register<Test>("testSmoke") {
    group = "verification"
    description = "Run MCP HTTP smoke tests"
    testClassesDirs = mcpSmokeTestSourceSet.output.classesDirs
    classpath = mcpSmokeTestSourceSet.runtimeClasspath
    configureMcpTestRuntime()
}

tasks.register("testFast") {
    group = "verification"
    description = "Run grouped MCP unit tests without compiling or running HTTP smoke tests"
    dependsOn(testSession, testDex, testResource, testModels)
}

tasks.register("testStructured") {
    group = "verification"
    description = "Run all split MCP test tasks serially by responsibility"
    dependsOn(testSession, testDex, testResource, testModels, testSmoke)
}

tasks.check {
    dependsOn(testSmoke)
}

val generateMcpVersionResource = tasks.register("generateMcpVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/resources/mcpBuildInfo")
    outputs.dir(outputDir)
    doLast {
        val outputFile = outputDir.get().file("dexclub-mcp-version.txt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(resolveMcpVersion(), Charsets.UTF_8)
    }
}

tasks.processResources {
    dependsOn(generateMcpVersionResource)
    from(generateMcpVersionResource)
}
