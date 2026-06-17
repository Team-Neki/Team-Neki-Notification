package com.neki.notification.domain.model

/**
 * 렌더링 결과.
 */
data class RenderedMessage(
    val title: String,
    val body: String,
    val actualTone: MessageTone,
    val variableApplied: Boolean,
)
