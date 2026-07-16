package com.neki.notification.domain.service

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.model.SkipReason
import com.neki.notification.domain.policy.MessageRenderer
import com.neki.notification.domain.policy.ToneAssignmentPolicy

object NotificationProcessor {

    fun decide(
        target: SendTarget,
        type: NotificationType,
        alreadySent: Boolean,
    ): SendDecision {
        // 동의 필터는 읽기 쿼리(WHERE push_agreed = true)가 담당한다. 여기서는 중복 발송만 판정한다.
        if (alreadySent) {
            return SendDecision.Skip(SkipReason.ALREADY_SENT)
        }
        val tone = ToneAssignmentPolicy.assign(target.userId)
        val message = MessageRenderer.render(type, tone, target.variables)
        return SendDecision.Send(message)
    }
}
