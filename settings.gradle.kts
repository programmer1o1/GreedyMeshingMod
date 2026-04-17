pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.11"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle"
    shared {
        vcsVersion = "1.21.11"
        versions("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
        vers("26.1", "26.1")
        vers("26.1.1", "26.1.1")
        vers("26.1.2", "26.1.2")
    }
    create(rootProject)
}
