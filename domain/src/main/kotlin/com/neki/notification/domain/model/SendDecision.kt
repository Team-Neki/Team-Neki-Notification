package com.neki.notification.domain.model

sealed interface SendDecision {
    data class Send(val message: RenderedMessage) : SendDecision

    data class Skip(val reason: SkipReason) : SendDecision
}

enum class SkipReason {
    NO_CONSENT,

    ALREADY_SENT,
}
