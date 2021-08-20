import org.spongepowered.gradle.vanilla.repository.MinecraftPlatform

plugins {
    id("org.spongepowered.gradle.vanilla") version "0.2"
}

dependencies {
    api(project(":cloud-core"))
    implementation(project(":cloud-brigadier"))
    compileOnly("org.spongepowered:spongeapi:8.0.0-SNAPSHOT")
    compileOnly("org.spongepowered:sponge:1.16.5-8.0.0-SNAPSHOT")
}

minecraft {
    version("1.16.5")
    platform(MinecraftPlatform.JOINED)
}
