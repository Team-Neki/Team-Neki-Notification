package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone

/**
 * 유저별 결정적 톤 배정 (copy-spec §5): MessageTone.entries[floorMod(userId, 3)].
 * 음수 userId는 Math.floorMod로 안전하게 처리한다.
 */
object ToneAssignmentPolicy {
    fun assign(userId: Long): MessageTone =
        MessageTone.entries[Math.floorMod(userId, 3)]
}
