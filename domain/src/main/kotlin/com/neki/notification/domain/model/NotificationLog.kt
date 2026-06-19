package com.neki.notification.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 발송 이력 (batch-design §6 notification_log). 순수 도메인 모델 — infra가 JPA 엔티티로 매핑한다.
 *
 * 중복 방지 키: (userId, notificationType, businessDate) (copy-spec §7).
 */
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
        /**
         * 발송 결과로부터 이력 1건을 만든다. `sentAt`은 infra 적재 시점에 채워진다.
         */
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
