plugins {
    id("pragma.java-library")
    id("pragma.publishing")
}

description = "Build-time part of the pragma-log Quarkus extension."

dependencies {
    implementation(project(":pragma-log-quarkus"))
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core.deployment)
    implementation(libs.quarkus.arc.deployment)
    annotationProcessor(platform(libs.quarkus.bom))
    annotationProcessor(libs.quarkus.extension.processor)
}

// The Quarkus extension processor warns that it cannot generate config docs outside Maven.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.remove("-Werror")
}

// The Quarkus extension plugin configures this task with a Project reference (no configuration cache).
tasks.test {
    notCompatibleWithConfigurationCache("configured by the Quarkus extension plugin")
}
