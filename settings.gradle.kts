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
        google()
        mavenCentral()
        // JitPack нужен для библиотеки libadb-android (пейринг и протокол ADB)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Local ADB Manager"
include(":app")
