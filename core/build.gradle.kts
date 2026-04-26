plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.concurrent.TimeUnit

fun resolveCoreVersion(): String {
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

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":dexkit"))
            implementation(libs.kotlinx.serialization.json)
        }

        jvmMain.dependencies {
            implementation(libs.smali.dexlib2)
            implementation(libs.smali.baksmali)
            implementation(libs.arsclib)
            implementation(libs.jadx.core)
            implementation(libs.jadx.dex.input)
            implementation(libs.jadx.kotlin.metadata)
            implementation(libs.logback.classic)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

val generateCoreVersionResource = tasks.register("generateCoreVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/resources/coreBuildInfo")
    outputs.dir(outputDir)
    doLast {
        val outputFile = outputDir.get().file("dexclub-core-version.txt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(resolveCoreVersion(), Charsets.UTF_8)
    }
}

tasks.named("processJvmMainResources") {
    dependsOn(generateCoreVersionResource)
    (this as org.gradle.language.jvm.tasks.ProcessResources).from(generateCoreVersionResource)
}
