// Shared setup for every published Java module.
plugins {
    `java-library`
    jacoco
}

// Sources and javadoc jars are added by the publishing plugin (pragma.publishing).
val libs = versionCatalogs.named("libs")

tasks.withType<JavaCompile>().configureEach {
    // Baseline is Java 17 (ADR-02); CI also builds on newer JDKs.
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports.xml.required = true
}
