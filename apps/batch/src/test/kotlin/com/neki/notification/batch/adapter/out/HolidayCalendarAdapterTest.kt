package com.neki.notification.batch.adapter.out

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * HolidayCalendarAdapter 발송일 판정 통합 테스트 (jOOQ + Testcontainers PostgreSQL).
 *
 *  - 당일(offset 0) / 전날(offset -1) / 매칭 없음 → null / 여러 후보 중 발송일 일치만 선택
 *  - 오프셋 가드(B-6/L-4): 조회 윈도우(±SCAN_WINDOW=3)가 허용 오프셋(±2)을 덮어
 *    경계(offset -3)에서 누락되지 않음. 윈도우 밖(offset -4)은 미조회(문서화된 한계).
 */
@SpringBootTest
@Testcontainers
class HolidayCalendarAdapterTest {

    @Autowired private lateinit var adapter: HolidayCalendarAdapter
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS holiday (id BIGSERIAL PRIMARY KEY, holiday_date DATE NOT NULL, name VARCHAR(64) NOT NULL, notify_offset_days INT NOT NULL, CONSTRAINT uq_holiday_date UNIQUE (holiday_date))",
        )
        jdbc.execute("TRUNCATE holiday RESTART IDENTITY")
    }

    private fun seed(date: LocalDate, name: String, offset: Int) {
        jdbc.update(
            "INSERT INTO holiday(holiday_date, name, notify_offset_days) VALUES (?, ?, ?)",
            date,
            name,
            offset,
        )
    }

    @Test
    fun `당일 발송 - businessDate가 공휴일 당일이면 매칭`() {
        val childrensDay = LocalDate.of(2026, 5, 5)
        seed(childrensDay, "어린이날", 0)

        assertEquals("어린이날", adapter.holidayToNotifyOn(childrensDay)?.name)
    }

    @Test
    fun `전날 발송 - offset -1이면 공휴일 하루 전에 매칭`() {
        val chuseok = LocalDate.of(2026, 9, 25)
        seed(chuseok, "추석 연휴", -1)

        // 발송일 = 9/25 - 1 = 9/24
        assertEquals("추석 연휴", adapter.holidayToNotifyOn(LocalDate.of(2026, 9, 24))?.name)
        // 당일(9/25)에는 매칭 안 됨
        assertNull(adapter.holidayToNotifyOn(chuseok))
    }

    @Test
    fun `매칭되는 공휴일이 없으면 null`() {
        assertNull(adapter.holidayToNotifyOn(LocalDate.of(2026, 6, 18)))
    }

    @Test
    fun `여러 후보 중 발송일이 일치하는 공휴일만 선택`() {
        val businessDate = LocalDate.of(2026, 5, 5)
        seed(LocalDate.of(2026, 5, 6), "다른날", 0) // notifyDate 5/6 → 불일치
        seed(LocalDate.of(2026, 5, 5), "어린이날", 0) // notifyDate 5/5 → 일치

        assertEquals("어린이날", adapter.holidayToNotifyOn(businessDate)?.name)
    }

    @Test
    fun `오프셋 -3 경계 - 윈도우(±3) 안이라 발송일에 매칭된다`() {
        val sendDate = LocalDate.of(2026, 9, 25)
        seed(sendDate.plusDays(3), "추석 연휴", -3) // notifyDate = (sendDate+3) - 3 = sendDate

        assertEquals("추석 연휴", adapter.holidayToNotifyOn(sendDate)?.name)
    }

    @Test
    fun `오프셋 -4 - 스캔 윈도우(±3) 밖이라 조회되지 않는다(문서화된 한계)`() {
        val sendDate = LocalDate.of(2026, 9, 25)
        seed(sendDate.plusDays(4), "연휴", -4) // notifyDate = sendDate 이지만 holiday_date가 윈도우 밖

        assertNull(adapter.holidayToNotifyOn(sendDate))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
