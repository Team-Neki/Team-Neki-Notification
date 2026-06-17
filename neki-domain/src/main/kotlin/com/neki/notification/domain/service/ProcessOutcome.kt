package com.neki.notification.domain.service

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.RenderedMessage

/**
 * 단일 발송 대상에 대한 처리 판정 결과.
 *
 * - [Send]: 발송해야 하며, 배정된 톤과 렌더링된 문구를 동봉한다.
 * - [Skip]: 발송하지 않으며, 그 사유를 동봉한다.
 */
sealed interface ProcessOutcome {
    /**
     * @property assignedTone 유저 결정적 배정 톤(A/B 코호트 키). 폴백 전 값.
     * @property message 렌더링 결과. [RenderedMessage.actualTone]은 폴백 후 실제 발송 톤이며,
     *   폴백이 없으면 [assignedTone]과 같다. notification_log.message_tone에는 actualTone을 저장한다.
     */
    data class Send(
        val assignedTone: MessageTone,
        val message: RenderedMessage,
    ) : ProcessOutcome

    data class Skip(val reason: SkipReason) : ProcessOutcome
}

/**
 * 발송 스킵 사유.
 */
enum class SkipReason {
    /** 푸시 수신 미동의. */
    NOT_CONSENTED,

    /** 동일 (유저, 타입, 영업일)로 이미 발송됨. */
    ALREADY_SENT,
}
