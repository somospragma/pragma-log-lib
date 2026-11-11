// SonarCloud analysis (Quality stage): `./gradlew test sonar` with SONAR_TOKEN set.
// Organization and project key come from the environment so the pipeline decides where to report.
plugins {
    alias(libs.plugins.sonarqube)
    // Loaded once here so every module shares the same Maven Central build service; otherwise modules that
    // add their own plugins (Quarkus) get a second copy and publishing fails.
    id("pragma.publishing") apply false
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", providers.environmentVariable("SONAR_ORGANIZATION").filter { it.isNotBlank() }.getOrElse("somospragma"))
        property("sonar.projectKey", providers.environmentVariable("SONAR_PROJECT_KEY").filter { it.isNotBlank() }.getOrElse("somospragma_pragma-log-lib"))
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
