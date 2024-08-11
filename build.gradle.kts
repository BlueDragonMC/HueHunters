plugins {
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.bluedragonmc.games.huehunters"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.BlueDragonMC.Server:common:67e9260fe2") {
        exclude(group = "org.tinylog")
    }
    implementation("net.minestom:minestom-snapshots:461c56e749")
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    implementation("ch.qos.logback:logback-classic:1.4.12")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

kotlin {
    jvmToolchain(21)
}