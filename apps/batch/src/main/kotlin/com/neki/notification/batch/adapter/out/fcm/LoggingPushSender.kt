package com.neki.notification.batch.adapter.out.fcm

import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.RenderedMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "neki.fcm", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.warn(
            "FCM 발송 비활성(neki.fcm.enabled != true): 실제 발송 없이 전 건 SKIPPED 처리됩니다. " +
                "운영 환경이라면 neki.fcm.enabled=true 와 credentials-location 설정을 즉시 점검하세요.",
        )
    }

    override fun send(token: String, message: RenderedMessage): NotificationStatus {
        log.info("[FCM 비활성] 발송 생략 token={} title={}", token, message.title)
        return NotificationStatus.SKIPPED
    }
}
