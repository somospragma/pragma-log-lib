plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.maven.publish.plugin)
    implementation(libs.errorprone.plugin)
    implementation(libs.spotbugs.plugin)
}
