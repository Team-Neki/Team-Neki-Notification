package com.neki.notification.batch

import com.neki.notification.infra.jooq.Tables.NOTIFICATION_LOG
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Flyway 마이그레이션(V1 앱 스키마)과 jOOQ 생성 타입의 정합성 검증 (H-3의 jOOQ판).
 *
 * Flyway 활성 + `initialize-schema=never` 로 컨텍스트를 띄워 Flyway가 소유 테이블을 만든다.
 * jOOQ 생성 타입(V1 DDL 기반)으로 그 테이블의 전체 컬럼을 SELECT 했을 때 성공하면,
 * 마이그레이션 DDL ↔ 생성 타입 ↔ 실제 DB 스키마가 컬럼·타입에서 일치함을 의미한다.
 * (예: 후속 마이그레이션이 소유 테이블을 바꾸면서 codegen 대상에 반영되지 않으면 여기서 실패)
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.batch.jdbc.initialize-schema=never",
    ],
)
@Testcontainers
class SchemaMigrationValidationTest {

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `Flyway 스키마가 jOOQ 생성 타입과 정합한다`() {
        // 생성 타입의 모든 컬럼을 명시 조회 — DB에 없는 컬럼이면 SQL 단계에서 실패한다.
        // (공휴일은 인메모리로 이관되어 소유 테이블/생성 타입이 없다.)
        dsl.selectFrom(NOTIFICATION_LOG).limit(0).fetch()
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
