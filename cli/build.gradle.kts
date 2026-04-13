import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.tasks.Jar
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.dexclub.cli.MainKt"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.named<ShadowJar>("shadowJar") {
    group = "build"
    description = "打包 DexClub CLI 可执行 fat jar"
    archiveBaseName.set("dexclub-cli")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    mergeServiceFiles()
}

tasks.register("fatJar") {
    group = "build"
    description = "打包 DexClub CLI 可执行 fat jar"
    dependsOn(tasks.named("shadowJar"))
}

val windowsRuntimeDllNames = listOf(
    "libgcc_s_seh-1.dll",
    "libwinpthread-1.dll",
    "libstdc++-6.dll",
    "zlib1.dll",
)

fun runtimeSidecarCandidates(): List<File> {
    val pathEntries = (System.getenv("PATH") ?: "")
        .split(File.pathSeparatorChar)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(::File)

    val discovered = linkedMapOf<String, File>()
    for (entry in pathEntries) {
        if (!entry.exists() || !entry.isDirectory) continue
        for (dllName in windowsRuntimeDllNames) {
            if (discovered.containsKey(dllName)) continue
            val candidate = File(entry, dllName)
            if (candidate.isFile) {
                discovered[dllName] = candidate
            }
        }
        if (discovered.size == windowsRuntimeDllNames.size) {
            break
        }
    }
    return windowsRuntimeDllNames.mapNotNull { discovered[it] }
}

fun isWindowsHost(): Boolean =
    System.getProperty("os.name").lowercase().contains("windows")

tasks.named<Sync>("installShadowDist") {
    doLast {
        if (!isWindowsHost()) return@doLast

        val libDir = destinationDir.resolve("lib")
        if (!libDir.exists()) return@doLast

        runtimeSidecarCandidates().forEach { sidecar ->
            sidecar.copyTo(libDir.resolve(sidecar.name), overwrite = true)
        }
    }
}

tasks.named<Zip>("shadowDistZip") {
    if (isWindowsHost()) {
        from(runtimeSidecarCandidates()) {
            into("${project.name}-shadow/lib")
        }
    }
}

tasks.named<CreateStartScripts>("startShadowScripts") {
    doLast {
        val windowsScript = windowsScript
        if (!windowsScript.exists()) return@doLast

        val original = windowsScript.readText(Charsets.UTF_8)
        val marker = "set CLASSPATH=%APP_HOME%\\lib\\dexclub-cli-all.jar"
        if (!original.contains(marker)) return@doLast
        if (original.contains("set PATH=%APP_HOME%\\lib;%PATH%")) return@doLast

        val patched = original.replace(
            marker,
            marker + "\r\nset PATH=%APP_HOME%\\lib;%PATH%",
        )
        windowsScript.writeText(patched, Charsets.UTF_8)
    }
}
