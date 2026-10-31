plugins {
    id("pragma.java-library")
    id("pragma.publishing")
}

description = "SLF4J binding of the pragma-log Quarkus extension: keeps arguments and key-values for masking."

dependencies {
    api(libs.slf4j.api)
    compileOnly(platform(libs.quarkus.bom))
    compileOnly("org.jboss.logmanager:jboss-logmanager")
}
