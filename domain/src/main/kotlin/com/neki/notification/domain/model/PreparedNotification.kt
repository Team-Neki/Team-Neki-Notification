package com.neki.notification.domain.model

import java.time.LocalDate

data class PreparedNotification(
    val target: SendTarget,
    val type: NotificationType,
    val message: RenderedMessage,
    val businessDate: LocalDate,
)
