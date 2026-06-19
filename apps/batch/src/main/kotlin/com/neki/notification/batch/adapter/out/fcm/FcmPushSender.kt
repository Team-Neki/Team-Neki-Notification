package com.neki.notification.batch.adapter.out.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.FcmResult
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

    override fun send(token: String, message: RenderedMessage): FcmResult =
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
            FcmResult.SUCCESS
        } catch (e: Exception) {
            log.warn("FCM 발송 실패 token={}", token, e)
            FcmResult.FAILED
        }
}
