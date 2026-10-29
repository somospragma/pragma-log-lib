// JMH benchmarks, not published (PERF-01). Run with: ./gradlew :benchmarks:jmh
// Pass JMH options with -PjmhArgs="...", e.g. -PjmhArgs="-wi 1 -i 2 -f 1 Masked"
plugins {
    java
}

val jmhSourceSet = sourceSets.create("jmh")

dependencies {
    "jmhImplementation"(project(":pragma-log-logback"))
    "jmhImplementation"(libs.jmh.core)
    "jmhAnnotationProcessor"(libs.jmh.generator)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.register<JavaExec>("jmh") {
    description = "Runs the JMH benchmarks."
    group = "verification"
    classpath = jmhSourceSet.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    val jmhArgs = providers.gradleProperty("jmhArgs").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        jmhArgs.get().split(" ").filter { it.isNotBlank() }
    })
}
