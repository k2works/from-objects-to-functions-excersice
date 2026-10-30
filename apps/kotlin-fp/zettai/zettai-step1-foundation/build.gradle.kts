plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.http4k.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}
