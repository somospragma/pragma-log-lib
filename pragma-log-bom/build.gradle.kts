plugins {
    `java-platform`
    id("pragma.publishing")
}

description = "Bill of materials that aligns all pragma-log module versions."

javaPlatform {
    allowDependencies()
}

dependencies {
    // Jackson 3 needs a matching jackson-annotations (shared with Jackson 2); see ADR-12.
    api(platform(libs.jackson.bom))
    constraints {
        api(project(":pragma-log-core"))
        api(project(":pragma-log-logback"))
        api(project(":pragma-log-spring-boot-starter"))
        api(project(":pragma-log-quarkus"))
        api(project(":pragma-log-quarkus-deployment"))
        api(project(":pragma-log-quarkus-slf4j"))
    }
}
