plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":modules:postgresql"))
    implementation(project(":modules:fcm"))
    implementation(project(":modules:scheduling"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.batch)
    // read/* 타깃 Reader가 NamedParameterJdbcTemplate를 직접 사용하므로 JDBC 스타터를 명시한다
    // (data-jpa 전이 의존에 암묵적으로 기대지 않도록 — H-2).
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.firebase.admin)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.archunit)
}
