plugins {
    id("fabric-loom") version "0.5-SNAPSHOT"
}

// Set up a testmod source set
val testmod by sourceSets.creating {
    val main = sourceSets.main.get()
    compileClasspath += main.compileClasspath
    runtimeClasspath += main.runtimeClasspath
    dependencies.add(implementationConfigurationName, main.output)
}

val testmodJar by tasks.creating(Jar::class) {
    archiveClassifier.set("testmod-dev")
    group = LifecycleBasePlugin.BUILD_GROUP
    from(testmod.output)
}

loom.unmappedModCollection.from(testmodJar)

tasks.withType(ProcessResources::class).configureEach {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType(Javadoc::class).configureEach {
    // options.links("https://maven.fabricmc.net/docs/yarn-${vers["fabricMc"]}+build.${vers["fabricYarn"]}/")
}

dependencies {
    minecraft("com.mojang:minecraft:${Versions.fabricMc}")
    mappings("net.fabricmc:yarn:${Versions.fabricMc}+build.${Versions.fabricYarn}:v2")
    modImplementation("net.fabricmc:fabric-loader:${Versions.fabricLoader}")
    modImplementation(fabricApi.module("fabric-command-api-v1", Versions.fabricApi))

    api(include(project(":cloud-core"))!!)
    implementation(include(project(":cloud-brigadier"))!!)

    include(project(":cloud-services"))
    include("io.leangen.geantyref:geantyref:${Versions.geantyref}")
}

publishing.publications.named("mavenJava", MavenPublication::class) {
    // add all the jars that should be included when publishing to maven
    artifact(tasks.remapJar) {
        builtBy(tasks.remapJar)
    }
    artifact(tasks.sourcesJar) {
        builtBy(tasks.remapSourcesJar)
    }

    // Loom is broken with project dependencies in the same build (because it resolves dependencies during configuration)
    // Please look away
    pom {
        withXml {
            val dependencies = asNode().appendNode("dependencies")
            sequenceOf("brigadier", "core").forEach {
                val depNode = dependencies.appendNode("dependency")
                depNode.appendNode("groupId", project.group)
                depNode.appendNode("artifactId", "cloud-$it")
                depNode.appendNode("version", project.version)
                depNode.appendNode("scope", "compile")
            }
        }
    }
}
