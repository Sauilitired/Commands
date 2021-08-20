import xyz.jpenilla.runpaper.task.RunServerTask

plugins {
    id("cloud.example-conventions")
    id("com.github.johnrengelman.shadow")
    id("xyz.jpenilla.run-paper") version "1.0.3"
}

dependencies {
    /* Cloud */
    implementation(project(":cloud-paper"))
    implementation(project(":cloud-annotations"))
    implementation(project(":cloud-minecraft-extras"))
    /* Extras */
    implementation("me.lucko", "commodore", Versions.commodore)
    implementation("net.kyori", "adventure-platform-bukkit", Versions.adventurePlatform)
    /* Bukkit */
    compileOnly("org.bukkit", "bukkit", Versions.bukkit)
}

tasks {
    shadowJar {
        dependencies {
            exclude(dependency("org.bukkit:bukkit:1.8.8-R0.1-SNAPSHOT"))
        }
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("1.17.1")
        runDirectory(file("run/latest"))
        javaLauncher.set(project.javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(16))
        })
    }

    // Setup a run task for each supported version
    mapOf(
            setOf("1.8.8", "1.9.4", "1.10.2", "1.11.2", "1.12.2") to 8,
            setOf("1.13.2", "1.14.4", "1.15.2") to 11,
            setOf("1.16.5", "1.17.1") to 16
    ).forEach { (minecraftVersions, javaVersion) ->
        for (version in minecraftVersions) {
            createVersionedRun(version, javaVersion)
        }
    }
}

fun TaskContainerScope.createVersionedRun(
        version: String,
        javaVersion: Int
) = register<RunServerTask>("runServer${version.replace(".", "_")}") {
    group = "cloud"
    pluginJars.from(shadowJar.flatMap { it.archiveFile })
    minecraftVersion(version)
    runDirectory(file("run/$version"))
    javaLauncher.set(project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    })
}
