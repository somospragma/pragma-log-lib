plugins {
    `java-platform`
    id("pragma.publishing")
}

description = "Bill of materials that aligns all pragma-log module versions."

dependencies {
    constraints {
        api(project(":pragma-log-core"))
        api(project(":pragma-log-logback"))
    }
}
