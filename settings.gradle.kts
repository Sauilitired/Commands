pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
        maven("https://repo.jpenilla.xyz/snapshots/")
    }
    includeBuild("build-logic")
}

plugins {
    id("ca.stellardrift.polyglot-version-catalogs") version "5.0.0"
}

rootProject.name = "cloud"

include(":cloud-bom")

// Core Modules
include(":cloud-core")
include(":cloud-services")
include(":cloud-tasks")
include(":cloud-annotations")

// Extension Modules
include(":cloud-kotlin-extensions")

// Discord Modules
setupDiscordModule("cloud-javacord")
setupDiscordModule("cloud-jda")

// Minecraft Modules
setupMinecraftModule("cloud-brigadier")
setupMinecraftModule("cloud-bukkit")
setupMinecraftModule("cloud-fabric")
setupMinecraftModule("cloud-paper")
setupMinecraftModule("cloud-velocity")
setupMinecraftModule("cloud-sponge")
setupMinecraftModule("cloud-sponge7")
setupMinecraftModule("cloud-bungee")
setupMinecraftModule("cloud-cloudburst")
setupMinecraftModule("cloud-minecraft-extras")

// IRC Modules
setupIrcModule("cloud-pircbotx")

// Example Modules
setupExampleModule("example-bukkit")
setupExampleModule("example-bungee")
//setupExampleModule("example-jda")
setupExampleModule("example-velocity")
setupExampleModule("example-sponge")

fun setupIrcModule(name: String) =
        setupSubproject(name, file("cloud-irc/$name"))

fun setupDiscordModule(name: String) =
        setupSubproject(name, file("cloud-discord/$name"))

fun setupMinecraftModule(name: String) =
        setupSubproject(name, file("cloud-minecraft/$name"))

fun setupExampleModule(name: String) =
        setupSubproject(name, file("examples/$name"))

fun setupSubproject(name: String, projectDirectory: File) = setupSubproject(name) {
    projectDir = projectDirectory
}

inline fun setupSubproject(name: String, block: ProjectDescriptor.() -> Unit) {
    include(name)
    project(":$name").apply(block)
}
