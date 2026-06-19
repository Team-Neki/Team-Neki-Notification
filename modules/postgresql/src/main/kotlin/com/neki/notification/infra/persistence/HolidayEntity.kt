package com.neki.notification.infra.persistence

import com.neki.notification.domain.model.Holiday
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 공휴일 JPA 엔티티 (batch-design §6 holiday). 본 앱 소유, 수동 시드.
 */
@Entity
@Table(
    name = "holiday",
    uniqueConstraints = [UniqueConstraint(name = "uq_holiday_date", columnNames = ["holiday_date"])],
)
class HolidayEntity(
    @Column(name = "holiday_date", nullable = false)
    val holidayDate: LocalDate,

    @Column(name = "name", nullable = false, length = 64)
    val name: String,

    @Column(name = "notify_offset_days", nullable = false)
    val notifyOffsetDays: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,
) {
    fun toDomain(): Holiday = Holiday(date = holidayDate, name = name, notifyOffsetDays = notifyOffsetDays)
}
