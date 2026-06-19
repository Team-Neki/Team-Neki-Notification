plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kover)
}

// 라이브러리 모듈이므로 boot jar는 비활성화하고 일반 jar만 생성한다.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.spring.boot.starter)
    implementation(libs.firebase.admin)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
}
