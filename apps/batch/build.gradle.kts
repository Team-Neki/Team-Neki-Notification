import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kover)
    alias(libs.plugins.jooq.codegen)
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
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.firebase.admin)
    runtimeOnly(libs.postgresql)

    // jOOQ 코드 생성: 소유 테이블(Flyway V1 DDL)에서만 타입 생성. 외부 소유 테이블은 plain SQL.
    jooqGenerator(libs.jooq.meta.extensions)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.archunit)
}

// 런타임 jOOQ(스타터, Boot BOM 관리)와 codegen 버전을 일치시킨다.
jooq {
    version.set(libs.versions.jooq.get())
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                logging = Logging.WARN
                generator.apply {
                    database.apply {
                        // 라이브 DB 없이 Flyway V1 DDL 스크립트에서 직접 스키마를 해석한다.
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties.addAll(
                            listOf(
                                Property().withKey("scripts")
                                    .withValue("src/main/resources/db/migration/V1__notification_schema.sql"),
                                Property().withKey("sort").withValue("semantic"),
                                Property().withKey("defaultNameCase").withValue("as_is"),
                                Property().withKey("unqualifiedSchema").withValue("none"),
                            ),
                        )
                    }
                    target.apply {
                        packageName = "com.neki.notification.infra.jooq"
                    }
                }
            }
        }
    }
}
