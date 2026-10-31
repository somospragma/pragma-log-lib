plugins {
    id("pragma.java-library")
    id("pragma.publishing")
}

description = "Spring Boot starter of pragma-log: JSON logs, masking and HTTP context with zero XML."

dependencies {
    api(project(":pragma-log-logback"))
    // Carries the request context (MDC) across Reactor threads in WebFlux (ADR-10).
    implementation(libs.context.propagation)

    // Provided by the application; versions come from its Spring Boot BOM.
    compileOnly(platform(libs.spring.boot.bom))
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.web)
    compileOnly(libs.jakarta.servlet.api)
    compileOnly(libs.reactor.core)
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.micrometer.tracing.brave)
    testImplementation(libs.micrometer.tracing.bridge.brave)
}

// Compatibility (ADR-12): run the same tests against Spring Boot 3.x, which uses Jackson 2.
val boot3Test by sourceSets.creating {
    java.srcDir("src/test/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

dependencies {
    "boot3TestImplementation"(project(":pragma-log-logback"))
    "boot3TestImplementation"(platform(libs.spring.boot3.bom))
    "boot3TestImplementation"(libs.spring.boot.starter.web)
    "boot3TestImplementation"(libs.spring.boot.starter.test)
    "boot3TestImplementation"(libs.spring.boot.starter.webflux)
    "boot3TestImplementation"(libs.context.propagation)
    // Boot 3.x has no tracing modules: actuator holds the auto-configuration.
    "boot3TestImplementation"(libs.spring.boot.starter.actuator)
    "boot3TestImplementation"(libs.micrometer.tracing.bridge.brave)
    "boot3TestRuntimeOnly"(libs.junit.launcher)
}

val boot3TestTask = tasks.register<Test>("boot3Test") {
    description = "Runs the starter tests against Spring Boot 3.x."
    group = "verification"
    testClassesDirs = boot3Test.output.classesDirs
    classpath = boot3Test.runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    dependsOn(boot3TestTask)
}
