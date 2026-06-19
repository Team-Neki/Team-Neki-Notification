package com.neki.notification.batch

import com.neki.notification.batch.application.HolidaySyncService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 공휴일 CSV → holiday 테이블 동기화 통합 테스트 (jOOQ upsert + Testcontainers).
 * 번들 holidays.csv를 적재하고 재동기화 멱등성을 단언한다.
 */
@SpringBootTest
@Testcontainers
class HolidaySyncServiceTest {

    @Autowired private lateinit var service: HolidaySyncService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS holiday (id BIGSERIAL PRIMARY KEY, holiday_date DATE NOT NULL, name VARCHAR(64) NOT NULL, notify_offset_days INT NOT NULL, CONSTRAINT uq_holiday_date UNIQUE (holiday_date))",
        )
        jdbc.execute("TRUNCATE holiday RESTART IDENTITY")
    }

    private fun count(): Int = jdbc.queryForObject("SELECT count(*) FROM holiday", Int::class.java)!!

    @Test
    fun `CSV를 holiday 테이블에 upsert하고 재동기화해도 멱등`() {
        val n = service.sync()
        assertTrue(n > 0, "번들 CSV에서 1건 이상 적재되어야 한다")
        assertEquals(n, count())

        // 재동기화 — onConflict upsert라 행 수 불변(중복 없음)
        service.sync()
        assertEquals(n, count())

        // 스팟체크: 광복절(2026-08-15) 적재
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM holiday WHERE name = '광복절' AND holiday_date = DATE '2026-08-15'",
                Int::class.java,
            ),
        )
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
