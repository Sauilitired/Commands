plugins {
    `java-platform`
}

indra {
    configurePublications {
        from(components["javaPlatform"])
    }
}

dependencies {
    constraints {
        for (subproject in rootProject.subprojects) {
            if (subproject == project) { // the bom itself
                continue
            }

            if (subproject.name.startsWith("example-")) {
                continue
            }

            api(project(subproject.path))
        }
    }
}
