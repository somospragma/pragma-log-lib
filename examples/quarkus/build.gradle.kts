// Example Quarkus app, not published. Run with: ./gradlew :examples:quarkus:quarkusDev
// then: curl -H 'X-Request-Id: req-1' localhost:8080/customers/1
plugins {
    java
    alias(libs.plugins.quarkus)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(project(":pragma-log-quarkus"))
    implementation(libs.quarkus.rest)
    implementation("io.quarkus:quarkus-rest-jackson")
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
