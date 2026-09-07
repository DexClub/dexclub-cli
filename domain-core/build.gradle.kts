import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val dexkitBindingModule = "io.github.dexclub:dexkit-binding:0.0.0-local"
val dexkitNativeLibraryDirProperty = "dexclub.dexkit.native.library.dir"
val externalDexKitNativeDir = providers.gradleProperty("dexkit.native.dir")
val dexClubBuildMetadata = dexClubBuildMetadata()

val generateCoreBuildInfo = tasks.register<GenerateDexClubBuildInfo>("generateCoreBuildInfo") {
    packageName.set("io.github.dexclub.core.impl.shared")
    objectName.set("CoreBuildInfo")
    visibility.set("internal")
    version.set(dexClubBuildMetadata.version)
    mcpContractVersion.set(dexClubBuildMetadata.mcpContractVersion)
    commit.set(dexClubBuildMetadata.commit)
    dirty.set(dexClubBuildMetadata.dirty)
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/buildInfo/kotlin"))
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir(generateCoreBuildInfo)
        }
        commonMain.dependencies {
            api(dexkitBindingModule)
            implementation(libs.kotlinx.serialization.json)
        }

        jvmMain.dependencies {
            implementation(libs.smali.dexlib2)
            implementation(libs.smali.baksmali)
            implementation(libs.arsclib)
            implementation(libs.jadx.core)
            implementation(libs.jadx.dex.input)
            implementation(libs.jadx.kotlin.metadata)
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

val jvmTestSourceSet = the<SourceSetContainer>()["jvmTest"]

fun Test.configureCoreJvmTestRuntime() {
    configureCoreJvmTestRuntime(requiresDexKitNative = true)
}

fun Test.configureCoreJvmTestRuntime(requiresDexKitNative: Boolean) {
    val externalNativeDir = externalDexKitNativeDir.orNull?.trim().orEmpty()
    if (externalNativeDir.isNotEmpty()) {
        systemProperty(dexkitNativeLibraryDirProperty, externalNativeDir)
    }
    if (requiresDexKitNative && externalNativeDir.isEmpty()) {
        dependsOn(gradle.includedBuild("dexkit-binding").task(":prepareDexKitDesktopNative"))
    }
}

fun registerCoreJvmTestTask(
    name: String,
    description: String,
    vararg classNames: String,
    requiresDexKitNative: Boolean = false,
) = tasks.register<Test>(name) {
    group = "verification"
    this.description = description
    testClassesDirs = jvmTestSourceSet.output.classesDirs
    classpath = jvmTestSourceSet.runtimeClasspath
    configureCoreJvmTestRuntime(requiresDexKitNative)
    filter {
        classNames.forEach(::includeTestsMatching)
    }
}

tasks.named<Test>("jvmTest") {
    configureCoreJvmTestRuntime()
}

val testDex by registerCoreJvmTestTask(
    name = "testDex",
    description = "Run domain-core dex analysis, query, and export tests",
    "io.github.dexclub.core.impl.dex.DefaultDexAnalysisSearchTest",
    "io.github.dexclub.core.impl.dex.DefaultDexAnalysisInspectTest",
    "io.github.dexclub.core.impl.dex.DefaultDexAnalysisExportTest",
    "io.github.dexclub.core.impl.dex.ScopedContextCacheTest",
    "io.github.dexclub.core.impl.dex.DexQueryParserTest",
    requiresDexKitNative = true,
)

val testResourceManifest by registerCoreJvmTestTask(
    name = "testResourceManifest",
    description = "Run domain-core manifest decode and structured inspection tests",
    "io.github.dexclub.core.impl.resource.ResourceManifestServiceTest",
)

val testResourceTable by registerCoreJvmTestTask(
    name = "testResourceTable",
    description = "Run domain-core resource table decode tests",
    "io.github.dexclub.core.impl.resource.ResourceTableServiceTest",
)

val testResourceXml by registerCoreJvmTestTask(
    name = "testResourceXml",
    description = "Run domain-core XML decode tests",
    "io.github.dexclub.core.impl.resource.ResourceXmlServiceTest",
)

val testResourceEntry by registerCoreJvmTestTask(
    name = "testResourceEntry",
    description = "Run domain-core resource entry indexing and listing tests",
    "io.github.dexclub.core.impl.resource.ResourceEntryServiceTest",
)

val testResourceValue by registerCoreJvmTestTask(
    name = "testResourceValue",
    description = "Run domain-core resource value resolution and search tests",
    "io.github.dexclub.core.impl.resource.ResourceValueServiceTest",
)

val testWorkspace by registerCoreJvmTestTask(
    name = "testWorkspace",
    description = "Run domain-core workspace initialization, default service wiring, and runtime resolution tests",
    "io.github.dexclub.core.api.shared.CreateDefaultServicesTest",
    "io.github.dexclub.core.impl.workspace.DefaultWorkspaceServiceTest",
    "io.github.dexclub.core.impl.workspace.runtime.WorkspaceRuntimeResolverTest",
)

tasks.register("testResource") {
    group = "verification"
    description = "Run all domain-core resource tests by responsibility"
    dependsOn(
        testResourceManifest,
        testResourceTable,
        testResourceXml,
        testResourceEntry,
        testResourceValue,
    )
}

tasks.register("testFast") {
    group = "verification"
    description = "Run the grouped domain-core dex, resource, and workspace test entrypoints"
    dependsOn(testDex, tasks.named("testResource"), testWorkspace)
}

tasks.register("testStructured") {
    group = "verification"
    description = "Run all split domain-core JVM test entrypoints serially by responsibility"
    dependsOn(testDex, tasks.named("testResource"), testWorkspace)
}
