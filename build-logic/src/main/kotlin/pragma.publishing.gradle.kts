// Maven Central publishing (ADR-04). Signing is enabled only when the key is provided,
// so local builds and publishToMavenLocal work without credentials.
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name = project.name
        description = provider { project.description ?: "Pragma structured logging library" }
        url = "https://github.com/somospragma/pragma-log-lib"
        inceptionYear = "2025"
        // License from the maven-central environment (POM_LICENSE_NAME, POM_LICENSE_URL): Maven Central requires one.
        val licenseName = providers.gradleProperty("pomLicenseName")
        if (licenseName.isPresent) {
            licenses {
                license {
                    name = licenseName
                    url = providers.gradleProperty("pomLicenseUrl")
                }
            }
        }
        scm {
            url = "https://github.com/somospragma/pragma-log-lib"
            connection = "scm:git:git://github.com/somospragma/pragma-log-lib.git"
            developerConnection = "scm:git:ssh://git@github.com/somospragma/pragma-log-lib.git"
        }
        developers {
            developer {
                id = "somospragma"
                name = "Pragma"
                url = "https://github.com/somospragma"
            }
        }
    }
}
