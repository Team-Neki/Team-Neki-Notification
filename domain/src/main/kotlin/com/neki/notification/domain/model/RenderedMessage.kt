package com.neki.notification.domain.model

data class RenderedMessage(
    val title: String,
    val body: String,
    val actualTone: MessageTone,
    val variableApplied: Boolean,
)
