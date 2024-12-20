enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatypeOssSnapshots"
            mavenContent { snapshotsOnly() }
        }
    }
    includeBuild("gradle/build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatypeOssSnapshots"
            mavenContent { snapshotsOnly() }
        }
    }
}

rootProject.name = "cloud"

include(":cloud-bom")

// Core Modules
include(":cloud-core")
include(":cloud-services")
include(":cloud-annotations")

// Kotlin Extensions
setupKotlinModule("cloud-kotlin-extensions")
setupKotlinModule("cloud-kotlin-coroutines")
setupKotlinModule("cloud-kotlin-coroutines-annotations")

fun setupKotlinModule(name: String) =
        setupSubproject(name, file("cloud-kotlin/$name"))

fun setupSubproject(name: String, projectDirectory: File) = setupSubproject(name) {
    projectDir = projectDirectory
}

inline fun setupSubproject(name: String, block: ProjectDescriptor.() -> Unit) {
    include(name)
    project(":$name").apply(block)
}
