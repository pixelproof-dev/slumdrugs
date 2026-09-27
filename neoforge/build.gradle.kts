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
        register("client") {
            client()
            // ./gradlew runClient -Pworld=<save name> goes straight into that world, which is how
            // a change to what the game draws gets looked at without clicking through menus.
            providers.gradleProperty("world").orNull?.let { programArguments.addAll("--quickPlaySingleplayer", it) }
        }
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

// The server gives each person a name that fits their face (sim Looks), so it has to know which
// faces there are — and a server never loads assets. The skin folder is listed at build time
// into a data file the server can read. Dropping a skin into the folder is all it takes.
val npcLooks = tasks.register("npcLooks") {
    val skins = layout.projectDirectory.dir("src/main/resources/assets/slumdrugs/textures/entity/npc")
    val out = layout.buildDirectory.file("generated/npc-looks/data/slumdrugs/npc_looks.txt")
    inputs.dir(skins)
    outputs.file(out)
    doLast {
        val names = skins.asFile.listFiles { f -> f.name.endsWith(".png") }.orEmpty()
            .map { it.name.removeSuffix(".png") }.sorted()
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(names.joinToString("\n", postfix = "\n"))
    }
}
sourceSets.main { resources.srcDir(npcLooks.map { layout.buildDirectory.dir("generated/npc-looks").get() }) }
