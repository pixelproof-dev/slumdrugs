pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    // Lets Gradle download the JDK the mod targets.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "slumdrugs-mod"

// sim/      platform-neutral simulation: no Minecraft, no loader, no I/O.
// neoforge/ the platform layer: registries, blocks, entities, menus, networking.
include("sim")
include("neoforge")
