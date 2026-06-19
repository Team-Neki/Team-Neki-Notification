package com.neki.notification.batch.adapter.out

import com.neki.notification.infra.persistence.HolidayEntity
import com.neki.notification.infra.persistence.HolidayJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * HolidayCalendarAdapter 발송일 판정 단위 테스트 (Docker 불필요 — repository 모킹).
 *
 *  - 당일(offset 0): notifyDate == holidayDate
 *  - 전날(offset -1): notifyDate == holidayDate - 1
 *  - 매칭 없음 → null
 *  - 여러 후보 중 notifyDate가 일치하는 것만 선택
 *  - 오프셋 가드(B-6/L-4): 조회 윈도우가 허용 오프셋을 덮어 경계(±3)에서 누락되지 않음
 */
class HolidayCalendarAdapterTest {

    private val repository: HolidayJpaRepository = mockk()
    private val adapter = HolidayCalendarAdapter(repository)

    private fun entity(date: LocalDate, name: String, offset: Int) =
        HolidayEntity(holidayDate = date, name = name, notifyOffsetDays = offset)

    @Test
    fun `당일 발송 - businessDate가 공휴일 당일이면 매칭`() {
        val childrensDay = LocalDate.of(2026, 5, 5)
        every { repository.findByHolidayDateBetween(any(), any()) } returns
            listOf(entity(childrensDay, "어린이날", 0))

        val holiday = adapter.holidayToNotifyOn(childrensDay)

        assertEquals("어린이날", holiday?.name)
    }

    @Test
    fun `전날 발송 - offset -1이면 공휴일 하루 전에 매칭`() {
        val chuseok = LocalDate.of(2026, 9, 25)
        every { repository.findByHolidayDateBetween(any(), any()) } returns
            listOf(entity(chuseok, "추석 연휴", -1))

        // 발송일 = 9/25 - 1 = 9/24
        assertEquals("추석 연휴", adapter.holidayToNotifyOn(LocalDate.of(2026, 9, 24))?.name)
        // 당일(9/25)에는 매칭 안 됨
        assertNull(adapter.holidayToNotifyOn(chuseok))
    }

    @Test
    fun `매칭되는 공휴일이 없으면 null`() {
        every { repository.findByHolidayDateBetween(any(), any()) } returns emptyList()
        assertNull(adapter.holidayToNotifyOn(LocalDate.of(2026, 6, 18)))
    }

    @Test
    fun `여러 후보 중 발송일이 일치하는 공휴일만 선택`() {
        val businessDate = LocalDate.of(2026, 5, 5)
        every { repository.findByHolidayDateBetween(any(), any()) } returns listOf(
            entity(LocalDate.of(2026, 5, 6), "다른날", 0), // notifyDate 5/6 → 불일치
            entity(LocalDate.of(2026, 5, 5), "어린이날", 0), // notifyDate 5/5 → 일치
        )

        assertEquals("어린이날", adapter.holidayToNotifyOn(businessDate)?.name)
    }

    @Test
    fun `조회 윈도우는 허용 오프셋(±2)을 덮도록 ±3일을 스캔한다`() {
        val businessDate = LocalDate.of(2026, 5, 5)
        every { repository.findByHolidayDateBetween(any(), any()) } returns emptyList()

        adapter.holidayToNotifyOn(businessDate)

        verify {
            repository.findByHolidayDateBetween(
                businessDate.minusDays(3),
                businessDate.plusDays(3),
            )
        }
    }

    @Test
    fun `오프셋 -3 경계 - 윈도우 밖으로 새지 않고 발송일에 매칭된다`() {
        val holidayDate = LocalDate.of(2026, 9, 28)
        val sendDate = holidayDate.minusDays(3) // notifyDate = 9/25
        every { repository.findByHolidayDateBetween(any(), any()) } returns
            listOf(entity(holidayDate, "추석 연휴", -3))

        assertEquals("추석 연휴", adapter.holidayToNotifyOn(sendDate)?.name)
    }
}
