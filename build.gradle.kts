plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "dev.celestelove"
version = "1.0-SNAPSHOT"

val paperLibrary by configurations.creating
configurations.compileOnly.get().extendsFrom(paperLibrary)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")

    paperLibrary("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    paperLibrary("com.charleskorn.kaml:kaml:0.72.0")
    paperLibrary("dev.kord:kord-core:0.15.0")
    paperLibrary("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.22.0")
    paperLibrary("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.22.0")
    paperLibrary("org.jetbrains.exposed:exposed-core:0.57.0")
    paperLibrary("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    paperLibrary("org.xerial:sqlite-jdbc:3.47.1.0")
    paperLibrary("net.xyzsd.fluent:fluent-base:0.72")
    paperLibrary("net.xyzsd.fluent:fluent-functions-icu:0.72")
}

val generatePaperLibraries: TaskProvider<Task> = tasks.register("generatePaperLibraries") {
    val outputFile = layout.buildDirectory.file("generated-resources/paper-libraries.txt")
    inputs.files(paperLibrary)
    outputs.file(outputFile)
    doLast {
        val deps = paperLibrary.resolvedConfiguration.resolvedArtifacts.map { artifact ->
            val id = artifact.moduleVersion.id
            "${id.group}:${id.name}:${id.version}"
        }.sorted()
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(deps.joinToString("\n"))
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated-resources"))
}

tasks {
    processResources {
        dependsOn(generatePaperLibraries)
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    runServer {
        minecraftVersion("1.21.4")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}
