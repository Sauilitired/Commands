plugins {
    id("cloud.parent-build-logic")
    id("com.github.ben-manes.versions")
}

group = "cloud.commandframework"
version = "1.6.0-SNAPSHOT"
description = "Command framework and dispatcher for the JVM"

subprojects {
    if (name != "cloud-bom") {
        apply(plugin = "cloud.base-conventions")
    }
    if (!name.startsWith("example-")) {
        apply(plugin = "cloud.publishing-conventions")
    }
}
