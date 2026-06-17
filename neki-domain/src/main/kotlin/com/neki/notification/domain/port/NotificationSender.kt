package com.neki.notification.domain.port

import com.neki.notification.domain.model.RenderedMessage
import com.neki.notification.domain.model.SendTarget

/**
 * 실제 푸시 전송 포트 (예: FCM 어댑터). 도메인은 전송 수단을 알지 못한다.
 */
interface NotificationSender {
    fun send(target: SendTarget, message: RenderedMessage): SendResult
}

/**
 * 실제 전송 시도의 결과. 재시도/이력 기록 정책에서 사용한다.
 *
 * SKIPPED는 여기 없다 — 발송 스킵은 [com.neki.notification.domain.service.ProcessOutcome.Skip]
 * 경로로 NotificationSender를 거치기 전에 결정되기 때문이다. notification_log.fcm_result의
 * SKIPPED 값은 Skip outcome을 기록할 때 별도로 매핑한다.
 */
enum class SendResult {
    SUCCESS,
    FAILED,
}
