import org.spongepowered.gradle.plugin.config.PluginLoaders
import org.spongepowered.plugin.metadata.PluginDependency

plugins {
    id("org.spongepowered.gradle.plugin") version "1.1.1"
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(project(":cloud-sponge"))
    implementation(project(":cloud-minecraft-extras"))
}

sponge {
    apiVersion("8.0.0")
    plugin("cloud-example-sponge") {
        loader(PluginLoaders.JAVA_PLAIN)
        displayName("Cloud example Sponge plugin")
        description("Plugin to demonstrate and test the Sponge implementation of cloud")
        mainClass("cloud.commandframework.examples.sponge.CloudExamplePlugin")
        dependency("spongeapi") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

configurations {
    spongeRuntime {
        resolutionStrategy.cacheChangingModulesFor(1, "MINUTES")
    }
}
