// Static analysis of the published modules, run by the Quality and Security stages of the pipeline.
// - Error Prone: bug patterns at compile time (lint). Needs JDK 21+ to run, so builds on JDK 17 skip it.
// - PMD: unused code (dead code).
// - SpotBugs + FindSecBugs: bugs and security issues in the bytecode (SAST).
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    pmd
    id("net.ltgt.errorprone")
    id("com.github.spotbugs")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "errorprone"(libs.findLibrary("errorprone-core").get())
    "spotbugsPlugins"(libs.findLibrary("findsecbugs").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        isEnabled = JavaVersion.current() >= JavaVersion.VERSION_21
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
    }
}

pmd {
    toolVersion = libs.findVersion("pmd").get().requiredVersion
    ruleSetFiles = files(rootDir.resolve("config/pmd/dead-code.xml"))
    ruleSets = listOf()
    isConsoleOutput = true
}

spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = rootDir.resolve("config/spotbugs/exclude.xml")
}

// Only production code is analyzed; tests are checked by the compiler and Error Prone.
tasks.withType<Pmd>().configureEach { enabled = name == "pmdMain" }
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach { enabled = name == "spotbugsMain" }
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}

// Dependency scan (Security stage): writes gradle.lockfile with what the module ships, for OSV-Scanner.
dependencyLocking {
    lockAllConfigurations()
}
tasks.register("resolveShippedDependencies") {
    description = "Resolves the runtime and compile classpaths; run with --write-locks to record them."
    notCompatibleWithConfigurationCache("resolves configurations at execution time")
    doLast {
        listOf("compileClasspath", "runtimeClasspath").forEach { configurations.getByName(it).resolve() }
    }
}

// Minimum line coverage of each module's unit tests. A module may lower it with
// extra["minLineCoverage"] = "0.50" when its code is mainly tested elsewhere (e.g. the Quarkus example app).
tasks.withType<JacocoCoverageVerification>().configureEach {
    val minimum = (project.findProperty("minLineCoverage") as String? ?: "0.75").toBigDecimal()
    violationRules {
        rule {
            limit {
                counter = "LINE"
                this.minimum = minimum
            }
        }
    }
}
tasks.named("check") { dependsOn(tasks.withType<JacocoCoverageVerification>()) }
