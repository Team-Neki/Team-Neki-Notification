package com.neki.notification.batch.adapter.out.fcm

import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.RenderedMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * FCM 비활성(`neki.fcm.enabled`!=true) 환경용 [PushSender] (로컬/테스트/드라이런).
 *
 * 실제 발송 없이 로그만 남기고 [FcmResult.SKIPPED]를 반환한다. 컨텍스트가 항상 PushSender 빈을
 * 갖도록 보장해 FCM 자격증명 없이도 앱이 기동·배치 실행되게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "neki.fcm", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(token: String, message: RenderedMessage): FcmResult {
        log.info("[FCM 비활성] 발송 생략 token={} title={}", token, message.title)
        return FcmResult.SKIPPED
    }
}
