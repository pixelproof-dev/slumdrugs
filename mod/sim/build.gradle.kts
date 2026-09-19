// The simulation module. It must never gain a Minecraft, loader or Bukkit dependency:
// that constraint is what keeps the rules testable without launching a game.
dependencies {
    // Intentionally empty. If something here needs a dependency, it belongs in the platform layer.
}

val simChecks by tasks.registering(JavaExec::class) {
    description = "Runs the dependency-free regression assertions."
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.lucas.slumdrugs.sim.SimChecks")
}
tasks.named("check") { dependsOn(simChecks) }
// The checks are a plain main(), not JUnit; stop Gradle failing on an empty test suite.
tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
