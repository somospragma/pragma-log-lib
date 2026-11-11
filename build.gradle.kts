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
    }
}

// Only the published library is analyzed: examples and benchmarks are not shipped.
listOf(":examples", ":examples:plain-java", ":examples:spring-mvc", ":examples:spring-webflux",
    ":examples:quarkus", ":benchmarks").forEach { path ->
    project(path) { sonar { isSkipProject = true } }
}

// The Quarkus extension runs only inside a Quarkus app, so most of it is tested by the examples/quarkus tests. The
// reports SonarCloud reads also count that coverage when those tests ran in the same build, as in the pipeline's
// `./gradlew test sonar`; a plain `test` of these modules does not run them.
listOf(":pragma-log-quarkus", ":pragma-log-quarkus-deployment", ":pragma-log-quarkus-slf4j").forEach { path ->
    project(path) {
        tasks.withType<JacocoReport>().matching { it.name == "jacocoTestReport" }.configureEach {
            val example = project(":examples:quarkus")
            mustRunAfter(":examples:quarkus:test")
            executionData.from(example.layout.buildDirectory.file("jacoco/test.exec").map { file ->
                listOf(file.asFile).filter { it.exists() }
            })
        }
    }
}
