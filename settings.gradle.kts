pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "dexclub-cli"

include(":cli")
include(":core")
include(":dexkit")

includeBuild("dexkit/vendor/DexKit") {
    name = "DexKit"
    dependencySubstitution {
        substitute(module("io.github.dexclub.dexkit:desktop-core")).using(project(":dexkit"))
        substitute(module("io.github.dexclub.dexkit:android-core")).using(project(":dexkit-android"))
    }
}
