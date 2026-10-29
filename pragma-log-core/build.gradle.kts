plugins {
    id("pragma.java-library")
    id("pragma.publishing")
}

description = "Framework-agnostic core of pragma-log: settings, schema and masking."

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.slf4j.api)
}
