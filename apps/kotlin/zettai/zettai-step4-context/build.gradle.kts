dependencies {
    implementation(rootProject.libs.http4k.core)
    implementation(rootProject.libs.http4k.server.jetty)

    implementation(rootProject.libs.postgresql)

    testImplementation(rootProject.libs.http4k.client.jetty)
    testImplementation(rootProject.libs.pesticide.core)
}
