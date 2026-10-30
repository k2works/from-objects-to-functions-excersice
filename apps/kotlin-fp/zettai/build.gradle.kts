plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        System.getProperty("cucumber.filter.tags")?.let {
            systemProperty("cucumber.filter.tags", it)
        }
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
