import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.dexclub.cli.MainKt"
}

val vendoredDexKitDir = rootProject.layout.projectDirectory.dir("dexkit/vendor/DexKit")
val externalDexKitNativeDir = providers.gradleProperty("dexkit.native.dir")

fun resolveCliVersion(): String {
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
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(gradle.includedBuild("DexKit").task(":dexkit:copyLibrary"))
}

val generateCliVersionResource = tasks.register("generateCliVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/resources/cliBuildInfo")
    outputs.dir(outputDir)
    doLast {
        val outputFile = outputDir.get().file("dexclub-cli-version.txt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(resolveCliVersion(), Charsets.UTF_8)
    }
}

tasks.processResources {
    dependsOn(generateCliVersionResource)
    from(generateCliVersionResource)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = resolveCliVersion()
    }
}

tasks.named<ShadowJar>("shadowJar") {
    group = "build"
    description = "打包 DexClub CLI 可执行 fat jar"
    archiveBaseName.set("dexclub-cli")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = resolveCliVersion()
    }
    mergeServiceFiles()
}

tasks.register("fatJar") {
    group = "build"
    description = "打包 DexClub CLI 可执行 fat jar"
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
            ${'$'}jarPath = Join-Path ${'$'}appHome 'lib/dexclub-cli-all.jar'

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
        dependsOn(gradle.includedBuild("DexKit").task(":dexkit:copyLibrary"))
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
