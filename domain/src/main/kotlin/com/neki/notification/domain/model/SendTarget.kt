package com.neki.notification.domain.model

/**
 * 발송 대상 1건 (copy-spec §8).
 */
data class SendTarget(
    val userId: Long,
    val fcmToken: String,
    val pushConsent: Boolean,
    val variables: Map<MessageVariable, String?> = emptyMap(),
)
