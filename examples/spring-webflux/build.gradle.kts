// Example Spring Boot WebFlux app, not published. Run with: ./gradlew :examples:spring-webflux:bootRun
// then: curl -H 'X-Request-Id: req-1' localhost:8080/orders/1
plugins {
    application
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":pragma-log-spring-boot-starter"))
    implementation(libs.spring.boot.starter.webflux)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    // Spring resolves @PathVariable names from parameter metadata (the Boot plugin adds this flag).
    options.compilerArgs.add("-parameters")
}

application {
    mainClass = "co.com.pragma.logging.examples.webflux.SpringWebFluxApp"
}

tasks.register("bootRun") {
    description = "Alias of run, for Spring Boot users."
    group = "application"
    dependsOn(tasks.named("run"))
}
