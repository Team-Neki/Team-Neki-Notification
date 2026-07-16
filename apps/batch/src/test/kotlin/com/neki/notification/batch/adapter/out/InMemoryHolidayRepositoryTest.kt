package com.neki.notification.batch.adapter.out

import com.neki.notification.domain.model.Holiday
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * InMemoryHolidayRepository 발송일 판정 단위 테스트 (Docker 불필요 — 순수 인메모리).
 *
 *  - 당일(offset 0) / 전날(offset -1) / 매칭 없음 → null / 여러 후보 중 발송일 일치만 선택
 *  - holiday_date 기준 멱등 병합, clear()로 격리
 *  - DB 시절 SCAN_WINDOW가 없어 오프셋이 허용치를 넘어도 발송일이 일치하면 매칭된다(WARN만 남김).
 */
class InMemoryHolidayRepositoryTest {

    private val repository = InMemoryHolidayRepository()

    @Test
    fun `당일 발송 - businessDate가 공휴일 당일이면 매칭`() {
        val childrensDay = LocalDate.of(2026, 5, 5)
        repository.upsertAll(listOf(Holiday(childrensDay, "어린이날", 0)))

        assertEquals("어린이날", repository.holidayToNotifyOn(childrensDay)?.name)
    }

    @Test
    fun `전날 발송 - offset -1이면 공휴일 하루 전에 매칭`() {
        val chuseok = LocalDate.of(2026, 9, 25)
        repository.upsertAll(listOf(Holiday(chuseok, "추석 연휴", -1)))

        // 발송일 = 9/25 - 1 = 9/24
        assertEquals("추석 연휴", repository.holidayToNotifyOn(LocalDate.of(2026, 9, 24))?.name)
        // 당일(9/25)에는 매칭 안 됨
        assertNull(repository.holidayToNotifyOn(chuseok))
    }

    @Test
    fun `매칭되는 공휴일이 없으면 null`() {
        assertNull(repository.holidayToNotifyOn(LocalDate.of(2026, 6, 18)))
    }

    @Test
    fun `여러 후보 중 발송일이 일치하는 공휴일만 선택`() {
        val businessDate = LocalDate.of(2026, 5, 5)
        repository.upsertAll(
            listOf(
                Holiday(LocalDate.of(2026, 5, 6), "다른날", 0), // notifyDate 5/6 → 불일치
                Holiday(LocalDate.of(2026, 5, 5), "어린이날", 0), // notifyDate 5/5 → 일치
            ),
        )

        assertEquals("어린이날", repository.holidayToNotifyOn(businessDate)?.name)
    }

    @Test
    fun `holiday_date 기준 멱등 병합 - 같은 날짜 재적재는 최신 값으로 덮어씀`() {
        val date = LocalDate.of(2026, 5, 5)
        repository.upsertAll(listOf(Holiday(date, "구명칭", 0)))
        repository.upsertAll(listOf(Holiday(date, "신명칭", 0)))

        assertEquals("신명칭", repository.holidayToNotifyOn(date)?.name)
    }

    @Test
    fun `허용 오프셋을 넘겨도 발송일이 일치하면 매칭 - DB SCAN_WINDOW 한계 없음`() {
        val sendDate = LocalDate.of(2026, 9, 25)
        repository.upsertAll(listOf(Holiday(sendDate.plusDays(4), "연휴", -4))) // notifyDate = sendDate

        assertEquals("연휴", repository.holidayToNotifyOn(sendDate)?.name)
    }

    @Test
    fun `clear로 스냅샷을 비우면 매칭 안 됨`() {
        val date = LocalDate.of(2026, 5, 5)
        repository.upsertAll(listOf(Holiday(date, "어린이날", 0)))
        repository.clear()

        assertNull(repository.holidayToNotifyOn(date))
    }
}
