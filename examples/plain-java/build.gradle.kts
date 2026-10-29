// Example app, not published. Run with: ./gradlew :examples:plain-java:run
plugins {
    application
}

dependencies {
    implementation(project(":pragma-log-logback"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

application {
    mainClass = "co.com.pragma.logging.examples.plain.PlainJavaApp"
}
