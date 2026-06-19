package com.neki.notification.domain.model

/**
 * 발송 대상 1건에 대한 처리 판정 (batch-design §5 Processor 단계).
 */
sealed interface SendDecision {
    /** 발송 확정 — 렌더링된 문구를 들고 Writer로 넘어간다. */
    data class Send(val message: RenderedMessage) : SendDecision

    /** 발송 제외 — 사유와 함께 스킵. */
    data class Skip(val reason: SkipReason) : SendDecision
}

/**
 * 발송 제외 사유 (batch-design §5 ②③).
 */
enum class SkipReason {
    /** 푸시 미동의 (TB_NOTIFICATION.push_agreed = false). */
    NO_CONSENT,

    /** 당일 동일 (userId, type, businessDate) 이미 발송됨 (copy-spec §7). */
    ALREADY_SENT,
}
