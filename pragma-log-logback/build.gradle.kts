plugins {
    id("pragma.java-library")
    id("pragma.publishing")
}

description = "Logback adapter of pragma-log: JSON encoder built on logstash-logback-encoder."

dependencies {
    api(project(":pragma-log-core"))
    api(libs.logstash.encoder)
    api(libs.logback.classic)
}
