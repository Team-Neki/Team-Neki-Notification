package com.neki.notification.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface HolidayJpaRepository : JpaRepository<HolidayEntity, Long> {
    fun findByHolidayDateBetween(start: LocalDate, end: LocalDate): List<HolidayEntity>
}
