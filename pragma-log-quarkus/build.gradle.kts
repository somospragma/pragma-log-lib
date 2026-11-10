plugins {
    id("pragma.java-library")
    id("pragma.publishing")
    alias(libs.plugins.quarkus.extension)
}

// Most of this module runs only inside a Quarkus app: examples/quarkus tests it in JVM and native mode.
extra["minLineCoverage"] = "0.50"

description = "Quarkus extension of pragma-log: JSON logs, masking and HTTP context on JBoss LogManager."

quarkusExtension {
    deploymentModule = "pragma-log-quarkus-deployment"
    // Our SLF4J binding replaces it: slf4j-jboss-logmanager formats arguments with toString() before any
    // formatter can mask them (ADR-11).
    excludedArtifacts = listOf("org.jboss.slf4j:slf4j-jboss-logmanager")
    // Loaded with JBoss LogManager and slf4j-api, which are parent-first too.
    parentFirstArtifacts = listOf("co.com.pragma.logging:pragma-log-quarkus-slf4j")
}

// The Quarkus extension plugin is not ready for Gradle 9: its validation resolves another project's
// classpath unsafely (rejected), and its tasks do not support the configuration cache.
tasks.named("validateExtension") {
    enabled = false
}
tasks.matching { it.name == "extensionDescriptor" || it.name == "validateExtension" }.configureEach {
    notCompatibleWithConfigurationCache("Quarkus extension plugin tasks")
}

dependencies {
    api(project(":pragma-log-core"))
    api(project(":pragma-log-quarkus-slf4j"))
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core)
    implementation(libs.quarkus.arc)
    // Optional: the HTTP context is registered only when the application has an HTTP server.
    compileOnly(libs.quarkus.vertx.http)
    // Optional: bodies are captured only for Quarkus REST endpoints (ENR-04).
    compileOnly(libs.quarkus.rest)
    // Optional: @LogEnrichment keeps its fields until a Mutiny Uni completes.
    compileOnly("io.smallrye.reactive:mutiny")
    compileOnly("io.vertx:vertx-codegen") // annotations of the Vert.x API, avoids compiler warnings
    annotationProcessor(platform(libs.quarkus.bom))
    annotationProcessor(libs.quarkus.extension.processor)

    testImplementation(platform(libs.quarkus.bom))
}

// The Quarkus extension processor warns that it cannot generate config docs outside Maven.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.remove("-Werror")
}
