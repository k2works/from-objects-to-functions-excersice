plugins {
    application
}

dependencies {
    implementation(rootProject.libs.http4k.core)
    implementation(rootProject.libs.http4k.server.jetty)

    implementation(rootProject.libs.postgresql)

    testImplementation(rootProject.libs.http4k.client.jetty)
    testImplementation(rootProject.libs.pesticide.core)
}

/**
 * 手元で動かして構造化ログを見るための起動設定（第 12 章）。
 *
 * ./gradlew :zettai-step5-monitoring:run
 */
application {
    mainClass.set("zettai.MainKt")
}
