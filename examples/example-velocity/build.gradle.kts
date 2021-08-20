plugins {
    id("com.github.johnrengelman.shadow")
    id("cloud.example-conventions")
}

val velocityRunClasspath by configurations.creating {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class, LibraryElements.JAR))
    }
}

repositories {
    ivy("https://versions.velocitypowered.com/download/") {
        patternLayout { artifact("[revision].[ext]") }
        metadataSources { artifact() }
        content { includeModule("com.velocitypowered", "velocity-proxy") }
    }
}

tasks {
    shadowJar {
        dependencies {
            exclude(dependency("com.velocitypowered:velocity-api"))
        }
    }
    build {
        dependsOn(shadowJar)
    }

    val pluginJar = shadowJar.map { it.outputs }
    val velocityRunFiles = velocityRunClasspath.asFileTree
    register("runVelocity", JavaExec::class) {
        group = "cloud"
        description = "Spin up a Velocity server environment"
        standardInput = System.`in`
        javaLauncher.set(project.javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })

        inputs.files(pluginJar)

        classpath(velocityRunFiles)
        workingDir = layout.projectDirectory.dir("run").asFile

        doFirst {
            // Prepare
            val modsDir = workingDir.resolve("plugins")
            if (!modsDir.isDirectory) {
                modsDir.mkdirs()
            }

            project.copy {
                into(modsDir.absolutePath)
                from(pluginJar) {
                    rename { "${rootProject.name}-${project.name}.jar" }
                }
            }
        }
    }
}

dependencies {
    api(project(":cloud-velocity"))
    api(project(":cloud-minecraft-extras"))
    api(project(":cloud-annotations"))
    annotationProcessor(compileOnly("com.velocitypowered", "velocity-api", Versions.velocityApi))
    velocityRunClasspath("com.velocitypowered", "velocity-proxy", "3.0.0")
}
