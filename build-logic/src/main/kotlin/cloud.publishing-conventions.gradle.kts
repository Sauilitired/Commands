plugins {
    id("net.kyori.indra.publishing")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    publishSnapshotsTo("incendo", "https://repo.incendo.org/content/repositories/snapshots/")

    github("Incendo", "cloud") {
        ci(true)
    }
    mitLicense()

    configurePublications {
        pom {
            developers {
                developer {
                    id.set("Sauilitired")
                    name.set("Alexander Söderberg")
                    url.set("https://alexander-soderberg.com")
                    email.set("contact@alexander-soderberg.com")
                }
            }
        }
    }
}
