package com.neki.notification.domain.model

/**
 * FCM 발송 결과 (batch-design §6 notification_log.fcm_result).
 */
enum class FcmResult {
    SUCCESS,
    FAILED,
    SKIPPED,
}
