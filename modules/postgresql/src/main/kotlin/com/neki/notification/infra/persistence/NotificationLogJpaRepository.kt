package com.neki.notification.infra.persistence

import com.neki.notification.domain.model.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/**
 * notification_log Spring Data JPA 리포지토리.
 */
interface NotificationLogJpaRepository : JpaRepository<NotificationLogEntity, Long> {
    fun existsByUserIdAndNotificationTypeAndBusinessDate(
        userId: Long,
        notificationType: NotificationType,
        businessDate: LocalDate,
    ): Boolean
}
