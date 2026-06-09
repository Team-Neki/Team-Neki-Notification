plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}
