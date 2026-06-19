package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidayCalendar
import com.neki.notification.domain.model.Holiday
import com.neki.notification.infra.persistence.HolidayJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

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
        const val WINDOW = 2L
    }
}
