pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }

        gradlePluginPortal()
    }

    val kotlinVersion = providers.gradleProperty("kotlin_version").get()
    val loomVersion = providers.gradleProperty("loom_version").get()

    plugins {
        kotlin("jvm") version kotlinVersion
        id("net.fabricmc.fabric-loom") version loomVersion
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "HateFrance"

stonecutter {
    create(rootProject) {
        versions("26.2", "26.3")
        vcsVersion = "26.2"
    }
}
