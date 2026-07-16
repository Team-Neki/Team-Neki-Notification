package com.neki.notification.domain.model

sealed interface SendDecision {
    data class Send(val message: RenderedMessage) : SendDecision

    data class Skip(val reason: SkipReason) : SendDecision
}

enum class SkipReason {
    // 동의 필터는 읽기 쿼리(WHERE push_agreed = true)가 단일 출처로 담당한다.
    ALREADY_SENT,
}
