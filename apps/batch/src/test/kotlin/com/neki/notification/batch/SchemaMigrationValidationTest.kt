package com.neki.notification.batch

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull

/**
 * Flyway 마이그레이션(V1 앱 스키마 + V2 Spring Batch 메타)과 JPA 엔티티의 정합성 검증 (H-3).
 *
 * Flyway 활성 + `ddl-auto=validate` + `initialize-schema=never` 로 컨텍스트를 띄운다.
 * 컨텍스트가 정상 로드되면, Flyway가 만든 테이블이 JPA 엔티티(NotificationLogEntity, HolidayEntity)와
 * 일치(컬럼·타입·길이)함을 의미한다. 마이그레이션 DDL과 엔티티가 어긋나면 이 테스트가 실패한다.
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never",
    ],
)
@Testcontainers
class SchemaMigrationValidationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `Flyway 마이그레이션이 JPA 엔티티 검증을 통과한다`() {
        assertNotNull(applicationContext)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
