plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
}

dependencies {
    kover(project(":zettai-step1-http"))
    kover(project(":zettai-step2-domain"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    repositories {
        mavenCentral()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    dependencies {
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testImplementation"(rootProject.libs.strikt.core)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}

// カバレッジはドメイン層を対象にする。
// アダプタ層（web）は受け入れテストで担保しており、行カバレッジで測る意味が薄い。
kover {
    reports {
        filters {
            includes { classes("zettai.domain.*") }
        }
        verify {
            rule {
                bound {
                    minValue = 80
                }
            }
        }
    }
}

// check 1 本でコンパイル・テスト・カバレッジ検証まで済ませる。
// ローカルと CI の判定を一致させるため、コマンドを増やさない。
tasks.named("check") {
    dependsOn("koverVerify")
}
