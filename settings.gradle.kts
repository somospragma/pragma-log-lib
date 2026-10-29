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
    "examples:plain-java",
    "examples:spring-mvc",
    "benchmarks",
)
