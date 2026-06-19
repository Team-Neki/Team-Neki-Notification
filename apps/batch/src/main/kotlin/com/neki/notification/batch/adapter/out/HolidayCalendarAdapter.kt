package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidayCalendar
import com.neki.notification.domain.model.Holiday
import com.neki.notification.infra.persistence.HolidayJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.math.abs

@Component
class HolidayCalendarAdapter(
    private val repository: HolidayJpaRepository,
) : HolidayCalendar {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun holidayToNotifyOn(businessDate: LocalDate): Holiday? {
        val candidates = repository.findByHolidayDateBetween(
            businessDate.minusDays(SCAN_WINDOW),
            businessDate.plusDays(SCAN_WINDOW),
        ).map { it.toDomain() }

        candidates
            .filter { abs(it.notifyOffsetDays.toLong()) > MAX_OFFSET_DAYS }
            .forEach {
                log.warn(
                    "공휴일 '{}'의 notifyOffsetDays={}가 허용 오프셋(±{}일)을 초과합니다. " +
                        "MAX_OFFSET_DAYS 상향 또는 시드 데이터 점검이 필요합니다.",
                    it.name,
                    it.notifyOffsetDays,
                    MAX_OFFSET_DAYS,
                )
            }

        return candidates.firstOrNull { it.notifyDate == businessDate }
    }

    private companion object {
        const val MAX_OFFSET_DAYS = 2L
        const val SCAN_WINDOW = MAX_OFFSET_DAYS + 1
    }
}
