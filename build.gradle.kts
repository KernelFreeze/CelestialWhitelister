plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "dev.celestelove"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    implementation("dev.kord:kord-core:0.15.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.22.0")
}

tasks {
    runServer {
        minecraftVersion("1.21.4")
    }

    shadowJar {
        archiveClassifier.set("")

        relocate("dev.kord", "dev.celestelove.whitelister.libs.kord")
        relocate("kotlin", "dev.celestelove.whitelister.libs.kotlin")
        relocate("kotlinx", "dev.celestelove.whitelister.libs.kotlinx")
        relocate("org.jetbrains", "dev.celestelove.whitelister.libs.jetbrains")
        relocate("org.intellij", "dev.celestelove.whitelister.libs.intellij")
        relocate("io.ktor", "dev.celestelove.whitelister.libs.ktor")
        relocate("com.github.shynixn.mccoroutine", "dev.celestelove.whitelister.libs.mccoroutine")

        mergeServiceFiles()
        minimize {
            exclude(dependency("dev.kord:.*"))
            exclude(dependency("io.ktor:.*"))
            exclude(dependency("com.github.shynixn.mccoroutine:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}
