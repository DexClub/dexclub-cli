import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.zip.ZipFile

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
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
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

val installShadowDistTask = tasks.named<Sync>("installShadowDist")
val dexKitAndroidAarDir = rootProject.layout.projectDirectory.dir("dexkit/vendor/DexKit/dexkit-android/build/outputs/aar")
val androidArm64NativeDir = layout.buildDirectory.dir("generated/dexkit/android-arm64-native")
val androidArm64InstallDir = layout.buildDirectory.dir("install/android-arm64/cli-shadow")

fun patchUnixLauncherForAndroidArm64(file: File) {
    val marker = "CLASSPATH=${'$'}APP_HOME/lib/dexclub-cli-all.jar"
    val injection = """
        CLASSPATH=${'$'}APP_HOME/lib/dexclub-cli-all.jar
        export DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR="${'$'}APP_HOME/lib/library"
    """.trimIndent()
    val original = file.readText(Charsets.UTF_8)
    if (!original.contains(marker)) {
        error("无法在 Unix 启动脚本中找到 classpath 标记: ${file.absolutePath}")
    }
    if (original.contains("DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR")) {
        return
    }
    file.writeText(original.replace(marker, injection), Charsets.UTF_8)
}

fun patchWindowsLauncherForAndroidArm64(file: File) {
    val marker = "set PATH=%APP_HOME%\\lib;%PATH%"
    val injection = """
        set PATH=%APP_HOME%\lib;%PATH%
        set DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR=%APP_HOME%\lib\library
    """.trimIndent().replace("\n", "\r\n")
    val original = file.readText(Charsets.UTF_8)
    if (!original.contains(marker)) {
        error("无法在 Windows 启动脚本中找到 PATH 标记: ${file.absolutePath}")
    }
    if (original.contains("DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR")) {
        return
    }
    file.writeText(original.replace(marker, injection), Charsets.UTF_8)
}

fun zipEntries(path: File): Map<String, ByteArray> {
    ZipFile(path).use { zip ->
        return zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .associate { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
    }
}

fun resolveDexKitAndroidReleaseAar(): File {
    val matches = dexKitAndroidAarDir.asFile
        .takeIf(File::isDirectory)
        ?.listFiles()
        ?.filter { it.isFile && it.name.endsWith("-release.aar") }
        .orEmpty()
        .sortedBy(File::getName)
    return matches.firstOrNull()
        ?: error("缺少 DexKit Android release AAR: ${dexKitAndroidAarDir.asFile.absolutePath}")
}

val extractAndroidArm64DexKitNative by tasks.registering {
    group = "build"
    description = "从 DexKit Android AAR 提取 android-arm64 专用 libdexkit.so"
    dependsOn(gradle.includedBuild("DexKit").task(":dexkit-android:assembleRelease"))
    doLast {
        val aarFile = resolveDexKitAndroidReleaseAar()
        check(aarFile.isFile) {
            "缺少 DexKit Android release AAR: ${aarFile.absolutePath}"
        }
        project.delete(androidArm64NativeDir)
        copy {
            from(zipTree(aarFile)) {
                include("jni/arm64-v8a/libdexkit.so")
                eachFile {
                    path = name
                }
                includeEmptyDirs = false
            }
            into(androidArm64NativeDir)
        }
        val nativeFile = androidArm64NativeDir.get().file("libdexkit.so").asFile
        check(nativeFile.isFile) {
            "未能提取 android-arm64 libdexkit.so: ${nativeFile.absolutePath}"
        }
    }
}

val installAndroidArm64ShadowDist by tasks.registering(Sync::class) {
    group = "distribution"
    description = "生成携带 bionic libdexkit.so 的 android-arm64 CLI 分发目录"
    dependsOn(installShadowDistTask, extractAndroidArm64DexKitNative)
    from(installShadowDistTask.map { it.destinationDir })
    into(androidArm64InstallDir)
    doLast {
        val distDir = androidArm64InstallDir.get().asFile
        val nativeDir = distDir.resolve("lib/library")
        windowsRuntimeDllNames.forEach { dllName ->
            distDir.resolve("lib/$dllName").delete()
        }
        nativeDir.mkdirs()
        copy {
            from(androidArm64NativeDir)
            into(nativeDir)
        }
        patchUnixLauncherForAndroidArm64(distDir.resolve("bin/cli"))
        patchWindowsLauncherForAndroidArm64(distDir.resolve("bin/cli.bat"))
    }
}

val androidArm64ShadowDistZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "打包 android-arm64 JVM CLI 分发包"
    dependsOn(installAndroidArm64ShadowDist)
    from(androidArm64InstallDir)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("cli-shadow-android-arm64.zip")
}

tasks.register("verifyAndroidArm64ShadowDist") {
    group = "verification"
    description = "验证 android-arm64 分发包与默认分发包存在可证明差异"
    dependsOn(tasks.named("shadowDistZip"), androidArm64ShadowDistZip)
    doLast {
        val desktopZip = layout.buildDirectory.file("distributions/cli-shadow.zip").get().asFile
        val androidZip = layout.buildDirectory.file("distributions/cli-shadow-android-arm64.zip").get().asFile
        check(desktopZip.isFile) { "缺少默认分发包: ${desktopZip.absolutePath}" }
        check(androidZip.isFile) { "缺少 android-arm64 分发包: ${androidZip.absolutePath}" }

        val desktopEntries = zipEntries(desktopZip)
        val androidEntries = zipEntries(androidZip)
        val androidNativePath = "lib/library/libdexkit.so"
        val unixScriptPath = "bin/cli"

        val androidNative = androidEntries[androidNativePath]
            ?: error("android-arm64 分发包缺少显式 native: $androidNativePath")
        check(desktopEntries[androidNativePath] == null) {
            "默认分发包不应包含 android-arm64 专用显式 native: $androidNativePath"
        }
        windowsRuntimeDllNames.forEach { dllName ->
            check(androidEntries["lib/$dllName"] == null) {
                "android-arm64 分发包不应包含 Windows sidecar: lib/$dllName"
            }
        }
        val unixScript = androidEntries[unixScriptPath]
            ?.toString(Charsets.UTF_8)
            ?: error("android-arm64 分发包缺少 Unix 启动脚本: $unixScriptPath")
        check(unixScript.contains("DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR")) {
            "android-arm64 启动脚本未设置显式 native 目录"
        }
        check(androidNative.isNotEmpty()) {
            "android-arm64 分发包中的 libdexkit.so 为空文件"
        }
    }
}

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
