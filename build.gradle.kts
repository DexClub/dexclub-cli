plugins {
    base
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
}

val dexClubBuildMetadata = resolveDexClubBuildMetadata(mcpContractVersion = 1)
extensions.extraProperties[DEXCLUB_BUILD_METADATA_KEY] = dexClubBuildMetadata

allprojects {
    version = dexClubBuildMetadata.version
}

tasks.register<GenerateDexClubVersionFile>("generateDexClubVersionFile") {
    version.set(dexClubBuildMetadata.version)
    mcpContractVersion.set(dexClubBuildMetadata.mcpContractVersion)
    commit.set(dexClubBuildMetadata.commit)
    dirty.set(dexClubBuildMetadata.dirty)
    outputFile.set(layout.buildDirectory.file("generated/distribution/VERSION"))
}

val cleanVendoredDexKitAndroidCxxCache = tasks.register<Delete>("cleanVendoredDexKitAndroidCxxCache") {
    group = "build"
    description = "Clear vendored DexKit Android CMake/.cxx caches so clean does not hit stale absolute paths after moving the repo"
    delete(
        layout.projectDirectory.dir("dexkit-binding/vendor/DexKit/dexkit-android/.cxx"),
        layout.projectDirectory.dir("dexkit-binding/vendor/DexKit/dexkit-android/build/intermediates/cxx"),
    )
}

tasks.named<Delete>("clean") {
    dependsOn(cleanVendoredDexKitAndroidCxxCache)
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Run the fast multi-module verification baseline for day-to-day boundary checks"
    dependsOn(
        ":app-service:testFast",
        ":cli-app:testFast",
        ":mcp-app:testFast",
        ":domain-core:testWorkspace",
    )
}
