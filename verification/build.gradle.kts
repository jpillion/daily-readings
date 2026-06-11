plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

repositories { mavenCentral() }

dependencies {
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { jvmToolchain(17) }

tasks.test {
    useJUnitPlatform()
    // Data artifacts live in /data at the repo root (move to app assets in Sprint 2).
    val dataDir = rootDir.parentFile.resolve("data")
    systemProperty("dataDir", dataDir.absolutePath)
    // The data files are test INPUTS: editing them must re-trigger the gate.
    inputs.dir(dataDir).withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging { events("passed", "failed", "skipped") }
}
