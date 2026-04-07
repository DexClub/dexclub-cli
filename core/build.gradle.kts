plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.jadx.core)
            implementation(libs.jadx.dex.input)
            implementation(libs.jadx.kotlin.metadata)
            implementation(libs.logback.classic)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
