package com.neki.notification.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/**
 * holiday Spring Data JPA 리포지토리.
 */
interface HolidayJpaRepository : JpaRepository<HolidayEntity, Long> {
    /** 발송일 판정 후보군: holiday_date가 [start, end] 범위인 공휴일. */
    fun findByHolidayDateBetween(start: LocalDate, end: LocalDate): List<HolidayEntity>
}
