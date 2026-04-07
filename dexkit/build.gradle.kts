import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val vendoredDexKitDir = rootProject.layout.projectDirectory.dir("dexkit/vendor/DexKit")

kotlin {
    android {
        namespace = "io.github.dexclub.dexkit"
        compileSdk = 36
        minSdk = 24
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.github.dexclub.dexkit:android-core:0.0.0-local")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.github.dexclub.dexkit:desktop-core:0.0.0-local")
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(gradle.includedBuild("DexKit").task(":dexkit:copyLibrary"))
    from(vendoredDexKitDir.dir("dexkit/build/library")) {
        include("**/*.so", "**/*.dll", "**/*.dylib")
        into("natives")
    }
}
