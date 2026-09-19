plugins {
    // Lets Gradle download the JDK the mod targets.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "slumdrugs-mod"

// sim/      platform-neutral simulation: no Minecraft, no loader, no I/O.
// neoforge/ the mod itself. Added once the 26.3 loader API is confirmed.
include("sim")
