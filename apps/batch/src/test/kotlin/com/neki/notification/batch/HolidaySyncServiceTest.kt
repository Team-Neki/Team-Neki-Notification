package com.neki.notification.batch

import com.neki.notification.batch.adapter.out.CsvHolidaySource
import com.neki.notification.batch.adapter.out.InMemoryHolidayRepository
import com.neki.notification.batch.application.HolidaySyncService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 공휴일 CSV → 인메모리 저장소 적재 단위 테스트 (Docker 불필요 — 클래스패스 픽스처 holidays-test.csv).
 * 재적재 멱등성과 적재 후 발송일 판정 조회를 단언한다.
 */
class HolidaySyncServiceTest {

    private val repository = InMemoryHolidayRepository()
    private val service = HolidaySyncService(CsvHolidaySource("holidays-test.csv"), repository)

    @Test
    fun `CSV를 인메모리에 적재하고 재적재해도 멱등`() {
        val n = service.sync()
        assertEquals(3, n, "픽스처 CSV의 3건이 적재되어야 한다")

        // 신정(2026-01-01, offset 0) → 발송일 2026-01-01에 조회됨
        assertEquals("신정", repository.holidayToNotifyOn(LocalDate.of(2026, 1, 1))?.name)
        // 삼일절(2026-03-01, offset -1) → 발송일 2026-02-28
        assertEquals("삼일절", repository.holidayToNotifyOn(LocalDate.of(2026, 2, 28))?.name)

        // 재적재 — holiday_date 기준 병합이라 멱등(건수·조회 결과 불변)
        assertEquals(3, service.sync())
        assertEquals("신정", repository.holidayToNotifyOn(LocalDate.of(2026, 1, 1))?.name)
    }
}
