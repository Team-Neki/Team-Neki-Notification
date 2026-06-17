package com.neki.notification.domain.service

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.policy.MessageRenderer
import com.neki.notification.domain.policy.ToneAssignmentPolicy

/**
 * 단일 발송 대상의 발송 여부를 판정하는 순수 도메인 서비스.
 *
 * 판정 순서(명세): ① 동의 재확인 → ② 중복 체크 → ③ 톤 배정 + 렌더링.
 * 스킵이 결정되면 렌더링하지 않는다(skip-early).
 *
 * 톤 배정과 렌더링은 각각 [ToneAssignmentPolicy]·[MessageRenderer]에 위임한다.
 *
 * dedup 조회(NotificationLogRepository.isAlreadySent)는 어댑터 책임이며,
 * 이 함수는 순수성을 유지하기 위해 그 결과(alreadySent)를 입력으로 받는다.
 */
object NotificationProcessor {
    fun process(
        target: SendTarget,
        type: NotificationType,
        alreadySent: Boolean,
    ): ProcessOutcome {
        if (!target.pushConsent) return ProcessOutcome.Skip(SkipReason.NOT_CONSENTED)
        if (alreadySent) return ProcessOutcome.Skip(SkipReason.ALREADY_SENT)

        val assignedTone = ToneAssignmentPolicy.assign(target.userId)
        val message = MessageRenderer.render(type, assignedTone, target.variables)
        return ProcessOutcome.Send(assignedTone, message)
    }
}
