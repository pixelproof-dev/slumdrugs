import java.util.zip.ZipFile

plugins {
    id("net.neoforged.moddev") version "2.0.147"
}

// Minecraft 26.3 shipped 2026-09-15. NeoForge has betas only for this generation;
// pin an exact build so an upstream break is a deliberate bump, not a surprise.
val neoforgeVersion = "26.3.0.8-beta"

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

// The subproject is called neoforge, so its jar was neoforge-0.1.0-SNAPSHOT.jar: a name that says
// which loader it is for and nothing about what it is, sitting in a mods folder beside a dozen
// others. It goes out under the mod's own name.
base {
    archivesName = "slumdrugs"
}

// sim is an internal library, not a mod of its own, so its classes have to travel inside this
// jar. A development run does not need that — the module is on the classpath there — which is
// why the mod ran for months and then failed on the first server it was installed on with
// NoClassDefFoundError: dev/lucas/slumdrugs/sim/drug/Strain. The check is part of the fix: a
// jar without those classes is not a mod, and the build should say so rather than ship it.
tasks.named<Jar>("jar") {
    val simJar = project(":sim").tasks.named<Jar>("jar")
    dependsOn(simJar)
    from(simJar.map { zipTree(it.archiveFile) })
    doLast {
        val carried = ZipFile(archiveFile.get().asFile).use { zip ->
            zip.entries().asSequence().count { it.name.startsWith("dev/lucas/slumdrugs/sim/") }
        }
        if (carried == 0) throw GradleException(
            "the mod jar carries no sim classes; it would not load outside a development run")
        logger.lifecycle("jar carries $carried sim classes")
    }
}

tasks.withType<ProcessResources>().configureEach {
    val props = mapOf("version" to project.version, "neoforgeVersion" to neoforgeVersion)
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}
