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

/**
 * [PushSender]의 Firebase Admin SDK 구현 (batch-design §4).
 *
 * `neki.fcm.enabled=true` 일 때만 활성화된다(미설정/로컬은 [LoggingPushSender]가 대체).
 * 토큰 단위 전송 실패는 배치를 중단시키지 않도록 [FcmResult.FAILED]로 흡수한다.
 */
@Component
@ConditionalOnProperty(prefix = "neki.fcm", name = ["enabled"], havingValue = "true")
class FcmPushSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

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
