plugins {
    java
}

group = "dev.lucas"
version = "1.2.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    testImplementation("io.papermc.paper:paper-api:26.3.build.+")
}

val regression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.lucas.slumdrugs.RegressionChecks")
}
tasks.check { dependsOn(regression) }
// Checks use a dependency-free Java runner, rather than JUnit discovery.
tasks.test { failOnNoDiscoveredTests.set(false) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

tasks.register<JavaExec>("exportPack") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.lucas.slumdrugs.PackExport")
    args(layout.buildDirectory.dir("distribution").get().asFile.absolutePath)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    jar {
        archiveFileName.set("SlumDrugs-${project.version}.jar")
        // Ships the setup notes, including the required join-time resource pack, inside the JAR.
        from(layout.projectDirectory.file("README.md"))
        from(layout.projectDirectory.file("docs/RESOURCE-PACK.md")) { into("docs") }
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}
