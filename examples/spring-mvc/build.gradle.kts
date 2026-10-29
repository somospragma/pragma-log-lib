// Example Spring Boot MVC app, not published. Run with: ./gradlew :examples:spring-mvc:bootRun
// then: curl -H 'X-Request-Id: req-1' localhost:8080/customers/1
plugins {
    application
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":pragma-log-spring-boot-starter"))
    implementation(libs.spring.boot.starter.web)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    // Spring MVC resolves @PathVariable names from parameter metadata (the Boot plugin adds this flag).
    options.compilerArgs.add("-parameters")
}

application {
    mainClass = "co.com.pragma.logging.examples.mvc.SpringMvcApp"
}

tasks.register("bootRun") {
    description = "Alias of run, for Spring Boot users."
    group = "application"
    dependsOn(tasks.named("run"))
}
