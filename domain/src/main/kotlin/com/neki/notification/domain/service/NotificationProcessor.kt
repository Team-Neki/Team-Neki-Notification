package com.neki.notification.domain.service

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.model.SkipReason
import com.neki.notification.domain.policy.MessageRenderer
import com.neki.notification.domain.policy.ToneAssignmentPolicy

/**
 * 발송 대상 처리 판정 (batch-design §5 Processor). 순수 함수.
 *
 * 순서: ① 푸시동의 확인 → ② 당일 중복 확인 → ③ 톤 배정 + 문구 렌더링.
 * 이력 조회 자체는 포트(NotificationLogStore) 책임이고, 여기서는 그 결과(alreadySent)를 입력으로 받는다 (copy-spec §7).
 */
object NotificationProcessor {

    fun decide(
        target: SendTarget,
        type: NotificationType,
        alreadySent: Boolean,
    ): SendDecision {
        if (!target.pushConsent) {
            return SendDecision.Skip(SkipReason.NO_CONSENT)
        }
        if (alreadySent) {
            return SendDecision.Skip(SkipReason.ALREADY_SENT)
        }
        val tone = ToneAssignmentPolicy.assign(target.userId)
        val message = MessageRenderer.render(type, tone, target.variables)
        return SendDecision.Send(message)
    }
}
