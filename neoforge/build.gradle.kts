plugins {
    id("net.neoforged.moddev") version "2.0.147"
}

// Minecraft 26.3 shipped 2026-09-15. NeoForge has betas only for this generation;
// pin an exact build so an upstream break is a deliberate bump, not a surprise.
val neoforgeVersion = "26.3.0.6-beta"

neoForge {
    version = neoforgeVersion
    runs {
        register("client") { client() }
        register("server") { server() }
    }
    mods {
        register("slumdrugs") { sourceSet(sourceSets.main.get()) }
    }
}

dependencies {
    implementation(project(":sim"))
}

tasks.withType<ProcessResources>().configureEach {
    val props = mapOf("version" to project.version, "neoforgeVersion" to neoforgeVersion)
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}
