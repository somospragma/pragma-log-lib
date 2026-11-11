// SonarCloud analysis (Quality stage): `./gradlew test sonar` with SONAR_TOKEN set.
// Organization and project key come from the environment so the pipeline decides where to report.
plugins {
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", providers.environmentVariable("SONAR_ORGANIZATION").getOrElse("somospragma"))
        property("sonar.projectKey", providers.environmentVariable("SONAR_PROJECT_KEY").getOrElse("somospragma_pragma-log-lib"))
        property("sonar.projectName", "pragma-log-lib")
        // Fails the build when the quality gate fails, like the rest of the Quality stage.
        property("sonar.qualitygate.wait", "true")
    }
}

// Only the published library is analyzed: examples and benchmarks are not shipped.
listOf(":examples", ":examples:plain-java", ":examples:spring-mvc", ":examples:spring-webflux",
    ":examples:quarkus", ":benchmarks").forEach { path ->
    project(path) { sonar { isSkipProject = true } }
}
