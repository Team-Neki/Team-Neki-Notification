plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":neki-domain"))
    implementation(project(":neki-infra"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.batch)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
}
