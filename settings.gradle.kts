pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pragma-log"

include(
    "pragma-log-bom",
    "pragma-log-core",
    "pragma-log-logback",
    "pragma-log-spring-boot-starter",
    "pragma-log-quarkus",
    "pragma-log-quarkus-slf4j",
    "pragma-log-quarkus-deployment",
    "examples:plain-java",
    "examples:spring-mvc",
    "examples:spring-webflux",
    "examples:quarkus",
    "benchmarks",
)
