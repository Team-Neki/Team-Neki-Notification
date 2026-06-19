package com.neki.notification.infra.persistence

import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "notification_log",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notification_log_user_type_date",
            columnNames = ["user_id", "notification_type", "business_date"],
        ),
    ],
    indexes = [
        Index(name = "ix_notification_log_business_date", columnList = "business_date"),
    ],
)
class NotificationLogEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    val notificationType: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(name = "message_tone", nullable = false, length = 16)
    val messageTone: MessageTone,

    @Column(name = "variable_applied", nullable = false)
    val variableApplied: Boolean,

    @Column(name = "title", nullable = false, length = 255)
    val title: String,

    @Column(name = "body", nullable = false, length = 500)
    val body: String,

    @Column(name = "business_date", nullable = false)
    val businessDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "fcm_result", nullable = false, length = 16)
    val fcmResult: FcmResult,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,
) {
    companion object {
        fun from(log: NotificationLog, sentAt: Instant): NotificationLogEntity = NotificationLogEntity(
            userId = log.userId,
            notificationType = log.notificationType,
            messageTone = log.messageTone,
            variableApplied = log.variableApplied,
            title = log.title,
            body = log.body,
            businessDate = log.businessDate,
            fcmResult = log.fcmResult,
            sentAt = log.sentAt ?: sentAt,
        )
    }
}
