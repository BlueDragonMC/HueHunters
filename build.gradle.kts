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
    implementation("com.github.BlueDragonMC.Server:common:f7bd8bdec9") {
        exclude(group = "org.tinylog")
    }
    implementation("net.kyori:adventure-text-minimessage:4.11.0")
    implementation("net.minestom:minestom-snapshots:461c56e749")
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    implementation("ch.qos.logback:logback-classic:1.4.12")

    compileOnly("com.github.bluedragonmc:rpc:fb16ef4cc5")
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.games.huehunters.server.MainKt"
    }
}

kotlin {
    jvmToolchain(21)
}