package com.neki.notification.domain.model

data class SendTarget(
    val userId: Long,
    val fcmToken: String,
    val pushConsent: Boolean,
    val variables: Map<MessageVariable, String?> = emptyMap(),
)
