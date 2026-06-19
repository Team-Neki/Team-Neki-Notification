package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidayCalendar
import com.neki.notification.domain.model.Holiday
import com.neki.notification.infra.persistence.HolidayJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [HolidayCalendar]의 JPA 구현 (batch-design §P5).
 *
 * businessDate 주변 [±WINDOW]일의 공휴일을 후보로 조회한 뒤,
 * `notifyDate(= date + notifyOffsetDays) == businessDate` 인 공휴일을 고른다.
 * 오프셋 계산을 Kotlin에서 처리해 DB 종속 날짜 연산을 피한다.
 */
@Component
class HolidayCalendarAdapter(
    private val repository: HolidayJpaRepository,
) : HolidayCalendar {

    override fun holidayToNotifyOn(businessDate: LocalDate): Holiday? {
        val candidates = repository.findByHolidayDateBetween(
            businessDate.minusDays(WINDOW),
            businessDate.plusDays(WINDOW),
        )
        return candidates
            .map { it.toDomain() }
            .firstOrNull { it.notifyDate == businessDate }
    }

    private companion object {
        /** notifyOffsetDays 절대값 상한(전날 -1 / 당일 0)을 넉넉히 커버. */
        const val WINDOW = 2L
    }
}
