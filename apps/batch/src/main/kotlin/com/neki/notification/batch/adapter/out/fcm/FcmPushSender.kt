package com.neki.notification.batch.adapter.out.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.RenderedMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "neki.fcm", name = ["enabled"], havingValue = "true")
class FcmPushSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("FCM 발송 활성화: Firebase Admin SDK로 실제 발송합니다.")
    }

    override fun send(token: String, message: RenderedMessage): NotificationStatus =
        try {
            firebaseMessaging.send(
                Message.builder()
                    .setToken(token)
                    .setNotification(
                        Notification.builder()
                            .setTitle(message.title)
                            .setBody(message.body)
                            .build(),
                    )
                    .build(),
            )
            NotificationStatus.SENT
        } catch (e: FirebaseMessagingException) {
            // 영구 실패(무효/만료 토큰 등)는 DEAD 로 못박아 재시도 대상에서 제외한다(ADR 0002).
            val status = statusFor(e.messagingErrorCode)
            log.warn("FCM 발송 실패(status={}) token={} code={}", status, token, e.messagingErrorCode, e)
            status
        } catch (e: Exception) {
            log.warn("FCM 발송 실패(status=FAILED) token={}", token, e)
            NotificationStatus.FAILED
        }

    companion object {
        /** 재시도해도 무의미한 영구 실패 코드 → DEAD. 그 외(UNAVAILABLE/INTERNAL/QUOTA_EXCEEDED/null 등)는 일시 실패 FAILED. */
        private val PERMANENT_ERRORS = setOf(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR,
        )

        internal fun statusFor(code: MessagingErrorCode?): NotificationStatus =
            if (code in PERMANENT_ERRORS) NotificationStatus.DEAD else NotificationStatus.FAILED
    }
}
