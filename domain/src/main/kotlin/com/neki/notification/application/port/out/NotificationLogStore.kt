package com.neki.notification.application.port.out

import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import java.time.LocalDate

interface NotificationLogStore {
    fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean

    fun save(log: NotificationLog)
}
