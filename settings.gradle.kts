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
    "examples:plain-java",
    "benchmarks",
)
