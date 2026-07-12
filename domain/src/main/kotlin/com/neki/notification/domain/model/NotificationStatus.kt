package com.neki.notification.domain.model

/**
 * 발송 1건의 라이프사이클 상태(ADR 0002). notification_log.status 에 저장된다.
 *
 * 현재는 발송 시점에 종료 상태(SENT/FAILED/DEAD/SKIPPED)를 한 번 기록한다.
 * PENDING(발송 전 클레임)은 claim-first outbox 도입 시 추가한다.
 */
enum class NotificationStatus {
    /** 실발송 성공(FcmPushSender). */
    SENT,

    /** 일시적 실패(네트워크/UNAVAILABLE/INTERNAL 등). 향후 재시도 대상. */
    FAILED,

    /** 영구 실패(무효/만료 토큰 등). 재시도 금지 — RDB 데드레터. */
    DEAD,

    /** 무발송 모드(LoggingPushSender, fcm.enabled=false). */
    SKIPPED,
}
