package com.neki.notification.domain.model

import java.time.Instant
import java.time.LocalDate

data class NotificationLog(
    val userId: Long,
    val notificationType: NotificationType,
    val messageTone: MessageTone,
    val variableApplied: Boolean,
    val title: String,
    val body: String,
    val businessDate: LocalDate,
    val fcmResult: FcmResult,
    val sentAt: Instant? = null,
    val id: Long? = null,
) {
    companion object {
        fun of(
            target: SendTarget,
            type: NotificationType,
            message: RenderedMessage,
            businessDate: LocalDate,
            fcmResult: FcmResult,
        ): NotificationLog = NotificationLog(
            userId = target.userId,
            notificationType = type,
            messageTone = message.actualTone,
            variableApplied = message.variableApplied,
            title = message.title,
            body = message.body,
            businessDate = businessDate,
            fcmResult = fcmResult,
        )
    }
}
