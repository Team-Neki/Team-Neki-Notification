plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kover)
}

allprojects {
    group = "com.neki.notification"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Docker Engine 29 (API 1.52, min 1.44) rejects the legacy default API version that
        // docker-java negotiates, returning an empty /info payload (HTTP 400). That makes
        // Testcontainers fail with "Could not find a valid Docker environment".
        // Pin a known-good Docker API version for the test JVM via both the docker-java
        // system property and the env var so all client strategies honor it.
        // Track: https://github.com/testcontainers/testcontainers-java/issues/11212 — remove once testcontainers natively supports Docker Engine 29+.
        systemProperty("api.version", "1.45")
        environment("DOCKER_API_VERSION", "1.45")
    }
}

// Aggregate coverage across all modules.
dependencies {
    kover(project(":domain"))
    kover(project(":modules:postgresql"))
    kover(project(":modules:fcm"))
    kover(project(":modules:scheduling"))
    kover(project(":apps:batch"))
}
