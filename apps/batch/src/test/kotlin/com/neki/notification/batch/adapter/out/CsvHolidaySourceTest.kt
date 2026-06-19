package com.neki.notification.batch.adapter.out

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * CsvHolidaySource 파싱 단위 테스트 (Docker 불필요 — 클래스패스 픽스처 holidays-test.csv).
 */
class CsvHolidaySourceTest {

    private val source = CsvHolidaySource("holidays-test.csv")

    @Test
    fun `주석·빈줄·헤더를 건너뛰고 파싱하며 offset 생략 시 0`() {
        val holidays = source.load()

        assertEquals(3, holidays.size)
        assertEquals(LocalDate.of(2026, 1, 1), holidays[0].date)
        assertEquals("신정", holidays[0].name)
        // offset 컬럼 생략 → 0
        assertEquals(0, holidays.first { it.name == "설날" }.notifyOffsetDays)
        // 음수 offset 파싱
        assertEquals(-1, holidays.first { it.name == "삼일절" }.notifyOffsetDays)
    }
}
