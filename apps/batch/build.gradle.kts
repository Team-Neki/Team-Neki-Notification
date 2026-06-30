import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kover)
    alias(libs.plugins.jooq.codegen)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":modules:fcm"))
    implementation(project(":modules:scheduling"))

    implementation(libs.spring.boot.starter)
    // GitOps prod Deployment가 httpGet /actuator/health/{liveness,readiness} (8080) 프로브를 사용하므로
    // 헬스 엔드포인트 노출용 web + actuator 스타터를 포함한다.
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.batch)
    // jOOQ 스타터가 jdbc(DataSource·DataSourceTransactionManager)를 전이로 제공하지만,
    // read/* 리더와 Spring Batch가 직접 의존하므로 JDBC 스타터를 명시한다(H-2).
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.firebase.admin)
    runtimeOnly(libs.postgresql)
    // prod 프로파일의 spring.datasource ENC(...) 값을 JASYPT_PASSWORD 로 복호화한다(서버 앱과 동일 공유 DB).
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5")

    // jOOQ 코드 생성: 소유 테이블(Flyway V1 DDL)에서만 타입 생성. 외부 소유 테이블은 plain SQL.
    jooqGenerator(libs.jooq.meta.extensions)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
}

// 배포는 bootJar(레이어드 실행 가능 jar)만 사용한다. plain jar를 비활성화해
// apps/batch/build/libs 에 jar가 하나만 남도록 하여 Dockerfile의 COPY(*.jar)를 모호하지 않게 한다.
tasks.named<Jar>("jar") {
    enabled = false
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
