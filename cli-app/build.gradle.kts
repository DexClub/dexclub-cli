import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

val dexkitNativeLibraryDirProperty = "dexclub.dexkit.native.library.dir"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.dexclub.cli.MainKt"
}

val vendoredDexKitDir = rootProject.layout.projectDirectory.dir("dexkit-binding/vendor/DexKit")
val externalDexKitNativeDir = providers.gradleProperty("dexkit.native.dir")
val dexClubBuildMetadata = dexClubBuildMetadata()

val generateCliBuildInfo = tasks.register<GenerateDexClubBuildInfo>("generateCliBuildInfo") {
    packageName.set("io.github.dexclub.cli")
    objectName.set("CliBuildInfo")
    visibility.set("internal")
    version.set(dexClubBuildMetadata.version)
    mcpContractVersion.set(dexClubBuildMetadata.mcpContractVersion)
    commit.set(dexClubBuildMetadata.commit)
    dirty.set(dexClubBuildMetadata.dirty)
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/buildInfo/kotlin"))
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateCliBuildInfo)
}

dependencies {
    implementation(project(":app-service"))
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

val cliTestSourceSet = the<SourceSetContainer>()["test"]

fun Test.configureCliTestRuntime() {
    configureCliTestRuntime(requiresDexKitNative = true)
}

fun Test.configureCliTestRuntime(requiresDexKitNative: Boolean) {
    useJUnitPlatform()
    val externalNativeDir = externalDexKitNativeDir.orNull?.trim().orEmpty()
    if (externalNativeDir.isNotEmpty()) {
        systemProperty(dexkitNativeLibraryDirProperty, externalNativeDir)
    }
    if (requiresDexKitNative && externalNativeDir.isEmpty()) {
        dependsOn(gradle.includedBuild("dexkit-binding").task(":prepareDexKitDesktopNative"))
    }
}

fun registerCliTestTask(
    name: String,
    description: String,
    vararg classNames: String,
    requiresDexKitNative: Boolean = false,
) = tasks.register<Test>(name) {
    group = "verification"
    this.description = description
    testClassesDirs = cliTestSourceSet.output.classesDirs
    classpath = cliTestSourceSet.runtimeClasspath
    configureCliTestRuntime(requiresDexKitNative)
    filter {
        classNames.forEach(::includeTestsMatching)
    }
}

tasks.test {
    configureCliTestRuntime()
}

val testHelp by registerCliTestTask(
    name = "testHelp",
    description = "Run CLI help and parser command tests",
    "io.github.dexclub.cli.CliHelpCommandTest",
)

val testWorkspace by registerCliTestTask(
    name = "testWorkspace",
    description = "Run CLI workspace lifecycle command tests",
    "io.github.dexclub.cli.CliWorkspaceCommandTest",
)

val testFailureRendering by registerCliTestTask(
    name = "testFailureRendering",
    description = "Run CLI failure rendering and debug stack trace tests",
    "io.github.dexclub.cli.CliAppFailureRenderingTest",
)

val testResource by registerCliTestTask(
    name = "testResource",
    description = "Run CLI Android resource command tests",
    "io.github.dexclub.cli.CliResourceCommandTest",
)

val testDexQuery by registerCliTestTask(
    name = "testDexQuery",
    description = "Run CLI dex query and inspect-method tests",
    "io.github.dexclub.cli.CliDexQueryCommandTest",
    requiresDexKitNative = true,
)

val testExport by registerCliTestTask(
    name = "testExport",
    description = "Run CLI code export tests",
    "io.github.dexclub.cli.CliExportCommandTest",
)

tasks.register("testFast") {
    group = "verification"
    description = "Run the fast CLI test suite that does not depend on Android resource builds"
    dependsOn(testHelp, testWorkspace, testFailureRendering)
}

tasks.register("testStructured") {
    group = "verification"
    description = "Run all split CLI test tasks grouped by command family"
    dependsOn(
        testHelp,
        testWorkspace,
        testFailureRendering,
        testResource,
        testDexQuery,
        testExport,
    )
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = dexClubBuildMetadata.version
    }
}

tasks.named<ShadowJar>("shadowJar") {
    group = "build"
    description = "Package the DexClub executable fat jar"
    archiveBaseName.set("dexclub")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = dexClubBuildMetadata.version
    }
    mergeServiceFiles()
}

tasks.register("fatJar") {
    group = "build"
    description = "Package the DexClub executable fat jar"
    dependsOn(tasks.named("shadowJar"))
}

val generateWindowsPowerShellLauncher = tasks.register("generateWindowsPowerShellLauncher") {
    val outputDir = layout.buildDirectory.dir("generated/scripts/shadowDist")
    outputs.dir(outputDir)
    doLast {
        val outputFile = outputDir.get().file("cli.ps1").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            ${'$'}ErrorActionPreference = 'Stop'

            ${'$'}scriptDir = Split-Path -Parent ${'$'}MyInvocation.MyCommand.Path
            ${'$'}appHome = (Resolve-Path (Join-Path ${'$'}scriptDir '..')).Path
            ${'$'}jarPath = Join-Path ${'$'}appHome 'lib/dexclub-all.jar'

            if (${ '$'}env:JAVA_HOME) {
                ${'$'}javaExe = Join-Path ${'$'}env:JAVA_HOME 'bin/java.exe'
                if (-not (Test-Path ${'$'}javaExe)) {
                    [Console]::Error.WriteLine("ERROR: JAVA_HOME is set to an invalid directory: ${'$'}env:JAVA_HOME")
                    [Console]::Error.WriteLine("Please set the JAVA_HOME variable in your environment to match the location of your Java installation.")
                    exit 1
                }
            } else {
                ${'$'}javaExe = 'java'
            }

            & ${'$'}javaExe -classpath ${'$'}jarPath io.github.dexclub.cli.MainKt @args
            exit ${'$'}LASTEXITCODE
            """.trimIndent(),
            Charsets.UTF_8,
        )
    }
}

val prepareDexKitNativeLibraries = tasks.register<Sync>("prepareDexKitNativeLibraries") {
    val externalNativeDir = externalDexKitNativeDir.orNull?.trim().orEmpty()
    if (externalNativeDir.isNotEmpty()) {
        from(File(externalNativeDir)) {
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }
    } else {
        dependsOn(gradle.includedBuild("dexkit-binding").task(":prepareDexKitDesktopNative"))
        from(vendoredDexKitDir.dir("dexkit/build/library")) {
            include("*.so", "*.dll", "*.dylib", "**/*.so", "**/*.dll", "**/*.dylib")
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }
    }
    into(layout.buildDirectory.dir("generated/native/shadowDist"))
}

distributions {
    named("shadow") {
        contents {
            from(rootProject.tasks.named<GenerateDexClubVersionFile>("generateDexClubVersionFile"))
            from(generateWindowsPowerShellLauncher) {
                into("bin")
            }
            from(prepareDexKitNativeLibraries) {
                into("lib")
            }
        }
    }
}

tasks.named<Sync>("installShadowDist") {
    dependsOn(generateWindowsPowerShellLauncher)
    dependsOn(prepareDexKitNativeLibraries)
}

tasks.named<Zip>("shadowDistZip") {
    dependsOn(generateWindowsPowerShellLauncher)
    dependsOn(prepareDexKitNativeLibraries)
}
